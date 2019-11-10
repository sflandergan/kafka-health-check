package com.deviceinsight.kafka.health;

import com.deviceinsight.kafka.health.strategy.HealthUpdateStrategy;
import com.deviceinsight.kafka.health.strategy.HealthUpdateStrategyFactory;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class KafkaConsumingHealthIndicator extends AbstractHealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumingHealthIndicator.class);
    private static final String CONSUMER_GROUP_PREFIX = "health-check-";

    private final Consumer<String, String> consumer;

    private final Producer<String, String> producer;

    private final String topic;
    private final Duration sendReceiveTimeout;
    private final Duration pollTimeout;
    private final Duration subscriptionTimeout;
    private final HealthUpdateStrategy healthUpdateStrategy;

    private final ExecutorService executor;
    private final AtomicBoolean running;
    private final Cache<String, String> cache;

    private KafkaCommunicationResult kafkaCommunicationResult;

    public KafkaConsumingHealthIndicator(
            KafkaHealthProperties kafkaHealthProperties,
            Map<String, Object> kafkaConsumerProperties, Map<String, Object> kafkaProducerProperties) {

        logger.info("Initializing kafka health check with properties: {}", kafkaHealthProperties);
        this.topic = kafkaHealthProperties.getTopic();
        this.sendReceiveTimeout = kafkaHealthProperties.getSendReceiveTimeout();
        this.pollTimeout = kafkaHealthProperties.getPollTimeout();
        this.subscriptionTimeout = kafkaHealthProperties.getSubscriptionTimeout();
        this.healthUpdateStrategy = HealthUpdateStrategyFactory.buildStrategy(kafkaHealthProperties
                .getHealthUpdate().getStrategy(), kafkaHealthProperties.getHealthUpdate().getProperties());

        Map<String, Object> kafkaConsumerPropertiesCopy = new HashMap<>(kafkaConsumerProperties);

        setConsumerGroup(kafkaConsumerPropertiesCopy);

        StringDeserializer deserializer = new StringDeserializer();
        StringSerializer serializer = new StringSerializer();

        this.consumer = new KafkaConsumer<>(kafkaConsumerPropertiesCopy, deserializer, deserializer);
        this.producer = new KafkaProducer<>(kafkaProducerProperties, serializer, serializer);

        this.executor = Executors.newSingleThreadExecutor();
        this.running = new AtomicBoolean(true);
        this.cache = Caffeine.newBuilder().expireAfterWrite(sendReceiveTimeout).build();

        this.kafkaCommunicationResult =
                KafkaCommunicationResult.failure(new RejectedExecutionException("Kafka Health Check is starting."));
    }

    @PostConstruct
    void subscribeAndSendMessage() throws InterruptedException {
        subscribeToTopic();

        if (kafkaCommunicationResult.isFailure()) {
            throw new BeanInitializationException("Kafka health check failed",
                    kafkaCommunicationResult.getException());
        }

        executor.submit(() -> {
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
                records.forEach(record -> cache.put(record.key(), record.value()));
            }
        });
    }

    @PreDestroy
    void shutdown() {
        running.set(false);
        executor.shutdownNow();
        producer.close();
        consumer.close();
    }

    private void setConsumerGroup(Map<String, Object> kafkaConsumerProperties) {
        try {
            String groupId = (String) kafkaConsumerProperties.getOrDefault(ConsumerConfig.GROUP_ID_CONFIG,
                    UUID.randomUUID().toString());
            kafkaConsumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG,
                    CONSUMER_GROUP_PREFIX + groupId + "-" + InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    private void subscribeToTopic() throws InterruptedException {

        final CountDownLatch subscribed = new CountDownLatch(1);

        logger.info("Subscribe to health check topic={}", topic);

        consumer.subscribe(Collections.singleton(topic), new ConsumerRebalanceListener() {

            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                // nothing to do her
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                logger.debug("Got partitions = {}", partitions);

                if (!partitions.isEmpty()) {
                    subscribed.countDown();
                }
            }
        });

        consumer.poll(pollTimeout);
        if (!subscribed.await(subscriptionTimeout.toMillis(), MILLISECONDS)) {
            throw new BeanInitializationException("Subscription to kafka failed, topic=" + topic);
        }

        this.kafkaCommunicationResult = KafkaCommunicationResult.success();
    }

    private String sendMessage() {

        try {
            return sendKafkaMessage();
        } catch (ExecutionException e) {
            logger.warn("Kafka health check execution failed.", e);
            this.kafkaCommunicationResult = KafkaCommunicationResult.failure(e);
        } catch (TimeoutException | InterruptedException e) {
            logger.warn("Kafka health check timed out.", e);
            this.kafkaCommunicationResult = KafkaCommunicationResult.failure(e);
        } catch (RejectedExecutionException e) {
            logger.debug("Ignore health check, already running...");
        }

        return null;
    }

    private String sendKafkaMessage() throws InterruptedException, ExecutionException, TimeoutException {

        String message = UUID.randomUUID().toString();

        logger.trace("Send health check message = {}", message);

        producer.send(new ProducerRecord<>(topic, message, message)).get(sendReceiveTimeout.toMillis(), MILLISECONDS);

        return message;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        String expectedMessage = sendMessage();
        if (expectedMessage == null) {
            goDown(builder);
            return;
        }

        long startTime = System.currentTimeMillis();
        while (true) {
            String receivedMessage = cache.getIfPresent(expectedMessage);
            if (expectedMessage.equals(receivedMessage)) {
                healthUpdateStrategy.handleSuccess(() -> builder);
                return;
            } else if (System.currentTimeMillis() - startTime > sendReceiveTimeout.toMillis()) {

                if (kafkaCommunicationResult.isFailure()) {
                    goDown(builder);
                } else {
                    healthUpdateStrategy.handleFailure(() ->
                            builder.withException(new TimeoutException("Sending and receiving took longer than " + sendReceiveTimeout + " ms"))
                                    .withDetail("topic", topic));
                }

                return;
            }
        }
    }

    private void goDown(Health.Builder builder) {
        healthUpdateStrategy.handleFailure(() ->
                builder.withException(kafkaCommunicationResult.getException()).withDetail("topic", topic));
    }

}
