package com.walmartlabs.concord.runtime.v2.runner;

import com.walmartlabs.concord.common.TimeProvider;

import java.time.Instant;

public class TestTimeProvider implements TimeProvider {

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public void sleep(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}
