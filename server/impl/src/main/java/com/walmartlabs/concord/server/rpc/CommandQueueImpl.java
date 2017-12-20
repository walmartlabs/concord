package com.walmartlabs.concord.server.rpc;

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
import com.walmartlabs.concord.rpc.*;
import com.walmartlabs.concord.server.agent.AgentCommand;
import com.walmartlabs.concord.server.agent.AgentCommandsDao;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Optional;
import java.util.UUID;

@Named
public class CommandQueueImpl extends TCommandQueueGrpc.TCommandQueueImplBase {

    private static final long RETRY_DELAY = 1000;

    private final AgentCommandsDao commandsDao;

    @Inject
    public CommandQueueImpl(AgentCommandsDao commandsDao) {
        this.commandsDao = commandsDao;
    }

    public void add(String agentId, Command cmd) {
        UUID commandId = UUID.randomUUID();
        commandsDao.insert(commandId, agentId, Commands.toMap(cmd));
    }

    @Override
    public void take(TCommandRequest request, StreamObserver<TCommandResponse> responseObserver) {
        String agentId = request.getAgentId();

        while (!Thread.currentThread().isInterrupted()) {
            Optional<AgentCommand> o = commandsDao.poll(agentId);

            if (o.isPresent()) {
                TCommandResponse resp = TCommandResponse.newBuilder()
                        .setCommand(convert(o.get()))
                        .build();

                responseObserver.onNext(resp);
            }

            try {
                Thread.sleep(RETRY_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        responseObserver.onCompleted();
    }

    private Any convert(AgentCommand cmd) {
        Command c = Commands.fromMap(cmd.getData());
        if (c instanceof CancelJobCommand) {
            return Any.pack(TCancelJobCommand.newBuilder()
                    .setInstanceId(((CancelJobCommand) c).getInstanceId())
                    .build());
        } else {
            throw new IllegalArgumentException("Unsupported command type: " + c.getClass());
        }
    }
}
