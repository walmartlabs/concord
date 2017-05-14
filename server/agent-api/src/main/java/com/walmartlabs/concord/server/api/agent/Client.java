package com.walmartlabs.concord.server.api.agent;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

public class Client {

    private final ManagedChannel channel;
    private final CommandQueue commandQueue;
    private final JobQueue jobQueue;

    public Client(String agentId, String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext(true)
                .build();

        this.commandQueue = new CommandQueueImpl(agentId, channel);
        this.jobQueue = new JobQueueImpl(agentId, channel);
    }

    public void stop() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public CommandQueue getCommandQueue() {
        return commandQueue;
    }

    public JobQueue getJobQueue() {
        return jobQueue;
    }
}
