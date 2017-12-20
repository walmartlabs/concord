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

import com.walmartlabs.concord.rpc.TProcessHeartbeatServiceGrpc.TProcessHeartbeatServiceBlockingStub;
import com.walmartlabs.concord.sdk.ClientException;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.TimeUnit;

public class ProcessHeartbeatServiceImpl implements ProcessHeartbeatService {

    private static final long PING_TIMEOUT = 5000;

    private final ManagedChannel channel;

    public ProcessHeartbeatServiceImpl(ManagedChannel channel) {
        this.channel = channel;
    }

    @Override
    public void ping(String instanceId) throws ClientException {
        TProcessHeartbeatServiceBlockingStub stub = TProcessHeartbeatServiceGrpc.newBlockingStub(channel);

        try {
            stub.withDeadlineAfter(PING_TIMEOUT, TimeUnit.MILLISECONDS)
                    .heartbeat(TProcessHeartbeatRequest.newBuilder()
                            .setInstanceId(instanceId)
                            .build());
        } catch (StatusRuntimeException e) {
            throw new ClientException(e.getMessage(), e);
        }
    }
}
