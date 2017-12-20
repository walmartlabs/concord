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

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.walmartlabs.concord.sdk.ClientException;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;

public class CommandQueueImpl implements CommandQueue {

    private final String agentId;
    private final ManagedChannel channel;

    public CommandQueueImpl(String agentId, ManagedChannel channel) {
        this.agentId = agentId;
        this.channel = channel;
    }

    private static Command convert(Any any) throws ClientException {
        if (any.is(TCancelJobCommand.class)) {
            TCancelJobCommand cmd;
            try {
                cmd = any.unpack(TCancelJobCommand.class);
            } catch (InvalidProtocolBufferException e) {
                throw new ClientException(e.getMessage(), e);
            }
            return new CancelJobCommand(cmd.getInstanceId());
        }

        throw new IllegalArgumentException("Unsupported command type: " + any.getTypeUrl());
    }

    @Override
    public void stream(CommandHandler handler) throws ClientException {
        TCommandQueueGrpc.TCommandQueueStub stub = TCommandQueueGrpc.newStub(channel);

        TCommandRequest req = TCommandRequest.newBuilder()
                .setAgentId(agentId)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        stub.take(req, new StreamObserver<TCommandResponse>() {
            @Override
            public void onNext(TCommandResponse value) {
                try {
                    Command cmd = convert(value.getCommand());
                    handler.onCommand(cmd);
                } catch (ClientException e) {
                    handler.onError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                handler.onError(t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
