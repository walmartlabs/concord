package com.walmartlabs.concord.server.metrics;

import com.google.inject.matcher.AbstractMatcher;

import java.lang.reflect.Method;

public class NoSyntheticMethodMatcher extends AbstractMatcher<Method> {

    public static final NoSyntheticMethodMatcher INSTANCE = new NoSyntheticMethodMatcher();

    private NoSyntheticMethodMatcher() {}

    @Override
    public boolean matches(Method method) {
        return !method.isSynthetic();
    }
}