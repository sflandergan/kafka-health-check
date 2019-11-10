package com.deviceinsight.kafka.health.strategy;

import org.springframework.boot.actuate.health.Health;

import java.util.function.Supplier;

public class ImmediateHealthUpdateStrategy implements HealthUpdateStrategy {

    @Override
    public void handleFailure(Supplier<Health.Builder> builderSupplier) {
        builderSupplier.get().down();
    }
}
