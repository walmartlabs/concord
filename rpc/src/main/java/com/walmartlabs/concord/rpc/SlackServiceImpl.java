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

import com.walmartlabs.concord.sdk.ClientException;
import com.walmartlabs.concord.sdk.SlackService;
import io.grpc.ManagedChannel;

import java.util.concurrent.TimeUnit;

public class SlackServiceImpl implements SlackService {

    private static final long REQUEST_TIMEOUT = 5000;

    private final ManagedChannel channel;

    public SlackServiceImpl(ManagedChannel channel) {
        this.channel = channel;
    }

    @Override
    public void notify(String instanceId, String channelId, String text) throws ClientException {
        TSlackNotificationServiceGrpc.TSlackNotificationServiceBlockingStub blockingStub = TSlackNotificationServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);

        TSlackNotificationResponse result = blockingStub.notify(TSlackNotificationRequest.newBuilder()
                .setInstanceId(instanceId)
                .setSlackChannelId(channelId)
                .setNotificationText(text)
            .build());

        if(!result.getOk()) {
            throw new ClientException("Error while trying to send slack notification: " + result.getError());
        }
    }
}
