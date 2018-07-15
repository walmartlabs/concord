package com.walmartlabs.concord.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PeriodicTask implements BackgroundTask {

    private static final Logger log = LoggerFactory.getLogger(PeriodicTask.class);

    private final long interval;
    private final long errorDelay;

    private Thread worker;

    public PeriodicTask(long interval, long errorDelay) {
        this.interval = interval;
        this.errorDelay = errorDelay;
    }

    @Override
    public void start() {
        if (interval < 0) {
            log.warn("start -> task is disabled");
            return;
        }

        this.worker = new Thread(this::run, this.getClass().getName());
        this.worker.start();
        log.info("start -> done");
    }

    @Override
    public void stop() {
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        log.info("stop -> done");
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                performTask();
                sleep(interval);
            } catch (Exception e) {
                log.warn("run -> task error: {}. Will retry in {}ms...", e.getMessage(), errorDelay);
                sleep(errorDelay);
            }
        }
    }

    protected abstract void performTask() throws Exception;

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
