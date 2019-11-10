package com.deviceinsight.kafka.health.strategy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;

public class HealthUpdateStrategyFactory {

    public static <T extends HealthUpdateStrategy> T buildStrategy(Class<T> clazz, Map<String, Object> configuration) {
        return Arrays.stream(clazz.getConstructors())
                .filter(HealthUpdateStrategyFactory::hasMatchingConstructor)
                .findFirst()
                .map(constructor -> {
                    try {
                        return clazz.cast(constructor.newInstance(configuration));
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException("Exception when constructing strategy", e);
                    }
                })
                .orElseGet(() -> buildWithDefaultConstructor(clazz));
    }

    private static <T extends HealthUpdateStrategy> T buildWithDefaultConstructor(Class<T> clazz) {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Exception when constructing strategy", e);
        }
    }

    private static <T> boolean hasMatchingConstructor(Constructor<T> constr) {
        return constr.getParameterCount() == 1 && Map.class.isAssignableFrom(constr.getParameterTypes()[0]);
    }

}
