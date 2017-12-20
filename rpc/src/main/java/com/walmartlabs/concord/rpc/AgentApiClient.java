package com.walmartlabs.concord.rpc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

public class AgentApiClient {

    private final ManagedChannel channel;
    private final CommandQueue commandQueue;
    private final JobQueue jobQueue;

    public AgentApiClient(String agentId, String host, int port) {
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
