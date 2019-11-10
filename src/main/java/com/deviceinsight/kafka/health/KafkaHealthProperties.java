package com.deviceinsight.kafka.health;

import com.deviceinsight.kafka.health.strategy.HealthUpdateStrategy;
import com.deviceinsight.kafka.health.strategy.ImmediateHealthUpdateStrategy;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class KafkaHealthProperties {

	private String topic = "health-checks";
	private Duration sendReceiveTimeout = Duration.ofMillis(2500);
	private Duration pollTimeout = Duration.ofMillis(200);
	private Duration subscriptionTimeout = Duration.ofSeconds(5);

	private HealthUpdate healthUpdate = new HealthUpdate();

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public Duration getSendReceiveTimeout() {
		return sendReceiveTimeout;
	}

	public void setSendReceiveTimeout(Duration sendReceiveTimeout) {
		this.sendReceiveTimeout = sendReceiveTimeout;
	}

	public Duration getPollTimeout() {
		return pollTimeout;
	}

	public void setPollTimeout(Duration pollTimeout) {
		this.pollTimeout = pollTimeout;
	}

	public Duration getSubscriptionTimeout() {
		return subscriptionTimeout;
	}

	public void setSubscriptionTimeout(Duration subscriptionTimeout) {
		this.subscriptionTimeout = subscriptionTimeout;
	}

	@Override
	public String toString() {
		return "KafkaHealthProperties{" + "topic='" + topic + '\'' + ", sendReceiveTimeout=" + sendReceiveTimeout +
				", pollTimeout=" + pollTimeout + ", subscriptionTimeout=" + subscriptionTimeout + '}';
	}

	public HealthUpdate getHealthUpdate() {
		return healthUpdate;
	}

	public void setHealthUpdate(HealthUpdate healthUpdate) {
		this.healthUpdate = healthUpdate;
	}

	public class HealthUpdate {

		private Class<? extends HealthUpdateStrategy> strategy = ImmediateHealthUpdateStrategy.class;

		private Map<String, Object> properties = new HashMap<>();

		public Class<? extends HealthUpdateStrategy> getStrategy() {
			return strategy;
		}

		public Map<String, Object> getProperties() {
			return properties;
		}

		public void setStrategy(Class<? extends HealthUpdateStrategy> strategy) {
			this.strategy = strategy;
		}

		public void setProperties(Map<String, Object> properties) {
			this.properties = properties;
		}
	}
}
