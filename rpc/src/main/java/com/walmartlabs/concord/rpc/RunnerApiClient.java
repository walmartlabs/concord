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

import com.walmartlabs.concord.sdk.EventService;
import com.walmartlabs.concord.sdk.KvService;
import com.walmartlabs.concord.sdk.SecretStoreService;
import com.walmartlabs.concord.sdk.SlackService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

public class RunnerApiClient {

    private final ManagedChannel channel;
    private final ProcessHeartbeatService heartbeatService;
    private final KvService kvService;
    private final SecretStoreService secretStoreService;
    private final EventService eventService;
    private final SlackService slackService;

    public RunnerApiClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext(true)
                .build();

        this.heartbeatService = new ProcessHeartbeatServiceImpl(channel);
        this.kvService = new KvServiceImpl(channel);
        this.secretStoreService = new SecretStoreServiceImpl(channel);
        this.eventService = new EventServiceImpl(channel);
        this.slackService = new SlackServiceImpl(channel);
    }

    public void stop() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public KvService getKvService() {
        return kvService;
    }

    public SecretStoreService getSecretStoreService() {
        return secretStoreService;
    }

    public EventService getEventService() {
        return eventService;
    }

    public SlackService getSlackService() {
        return slackService;
    }

    public ProcessHeartbeatService getHeartbeatService() {
        return heartbeatService;
    }
}
