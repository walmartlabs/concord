package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

@Named("sleep")
public class SleepTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SleepTask.class);

    public void ms(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void spam(String message, int delay, int count) {
        try {
            for (int i = 0; i < count; i++) {
                log.info("MESSAGE: {}", message);
                Thread.sleep(delay);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
