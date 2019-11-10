package com.deviceinsight.kafka.health.strategy;

import com.deviceinsight.kafka.health.KafkaHealthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = TestConfiguration.class)
public class HealthUpdateStrategyFactoryTest {

    @Autowired
    private KafkaHealthProperties properties;

    @Test
    public void testBuildingDelayedStrategy() {
        HealthUpdateStrategy strategy = HealthUpdateStrategyFactory.buildStrategy(
                properties.getHealthUpdate().getStrategy(), properties.getHealthUpdate().getProperties()
        );

        assertThat(strategy).isInstanceOf(DelayedHealthUpdateStrategy.class);
        DelayedHealthUpdateStrategy delayedStrategy = (DelayedHealthUpdateStrategy) strategy;
        assertThat(delayedStrategy.minimumDelay).isEqualTo(Duration.ofSeconds(5));
        assertThat(delayedStrategy.maximumDelay).isEqualTo(Duration.ofSeconds(10));
    }
}


@SpringBootApplication
class TestConfiguration {

    @Bean
    @ConfigurationProperties("kafka.health")
    public KafkaHealthProperties kafkaHealthProperties() {
        return new KafkaHealthProperties();
    }
}
