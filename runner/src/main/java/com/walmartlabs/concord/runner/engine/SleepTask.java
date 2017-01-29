package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.Task;

import javax.inject.Named;

@Named
public class SleepTask implements Task {

    @Override
    public String getKey() {
        return "sleep";
    }

    public void ms(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
