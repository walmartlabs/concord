package com.walmartlabs.concord.runner;

import com.walmartlabs.concord.runner.engine.RpcClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class ProcessHeartbeat {

    private static final Logger log = LoggerFactory.getLogger(ProcessHeartbeat.class);

    private static final long HEARTBEAT_INTERVAL = 5000;

    private final RpcClientImpl client;
    private Thread worker;

    @Inject
    public ProcessHeartbeat(RpcClientImpl client) {
        this.client = client;
    }

    public synchronized void start(String instanceId) {
        if (worker != null) {
            throw new IllegalArgumentException("Heartbeat worker is already running");
        }

        worker = new Thread(() -> {
            log.info("start ['{}'] -> running every {}ms", instanceId, HEARTBEAT_INTERVAL);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    client.getHeartbeatService().ping(instanceId);
                } catch (Exception e) {
                    log.warn("run -> heartbeat error: {}", e.getMessage());
                }

                try {
                    Thread.sleep(HEARTBEAT_INTERVAL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            log.info("start ['{}'] -> stopped", instanceId);
        }, "process-heartbeat");

        worker.start();
    }
}
