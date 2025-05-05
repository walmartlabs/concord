package com.walmartlabs.concord.runtime.v2.runner;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.walmartlabs.concord.common.TimeProvider;

public class TimeProviderModule implements Module {

    private final TimeProvider timeProvider;

    public TimeProviderModule(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(TimeProvider.class).toInstance(timeProvider);
    }
}
