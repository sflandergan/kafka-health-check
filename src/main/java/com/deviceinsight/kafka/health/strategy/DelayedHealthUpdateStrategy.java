package com.deviceinsight.kafka.health.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

public class DelayedHealthUpdateStrategy implements HealthUpdateStrategy {

    private static final Logger logger = LoggerFactory.getLogger(DelayedHealthUpdateStrategy.class);

    final Duration minimumDelay;
    final Duration maximumDelay;

    private volatile Instant downAt;

    public DelayedHealthUpdateStrategy(Map<String, Object> properties) {
        this(toDuration(properties.get("minimumDelay")), toDuration(properties.get("maximumDelay")));
    }

    private static Duration toDuration(Object property) {
        Assert.notNull(property, String.format("Property '%s' is mandatory", property));
        return Duration.parse(property.toString());
    }

    private DelayedHealthUpdateStrategy(Duration minimumDelay, Duration maximumDelay) {
        Assert.state(minimumDelay.compareTo(maximumDelay) <= 0,
                "Maximum delay must be greater or equals minimum");
        this.minimumDelay = minimumDelay;
        this.maximumDelay = maximumDelay;
    }

    @Override
    public void handleSuccess(Supplier<Health.Builder> builderSupplier) {
        builderSupplier.get().up();
        downAt = null;
    }


    @Override
    public void handleFailure(Supplier<Health.Builder> builderSupplier) {
        if (downAt == null) {
            Duration delay = calculateDelay();
            logger.debug("Marking health down in {}", delay);
            downAt = Instant.now().plus(delay);
        }

        if (Instant.now().isAfter(downAt)) {
            builderSupplier.get().down();
        }
    }

    private Duration calculateDelay() {
        long delta = (long) (maximumDelay.minus(minimumDelay).toMillis() * Math.random());
        return minimumDelay.plusMillis(delta);
    }
}
