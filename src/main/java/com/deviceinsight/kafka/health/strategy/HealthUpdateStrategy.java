package com.deviceinsight.kafka.health.strategy;

import org.springframework.boot.actuate.health.Health;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface HealthUpdateStrategy {

    void handleFailure(Supplier<Health.Builder> builderSupplier);

    default void handleSuccess(Supplier<Health.Builder> builderSupplier) {
        builderSupplier.get().up();
    }
}
