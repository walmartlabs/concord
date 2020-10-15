package com.walmartlabs.concord.server.queueclient.message;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class CommandRequest extends Message {

    private final UUID agentId;

    @JsonCreator
    public CommandRequest(
            @JsonProperty("agentId") UUID agentId) {
        super(MessageType.COMMAND_REQUEST);
        this.agentId = agentId;
    }

    public UUID getAgentId() {
        return agentId;
    }

    @Override
    public final String toString() {
        return "CommandRequest{" +
                "correlationId='" + getCorrelationId() + "', " +
                "agentId='" + agentId + "'" +
                '}';
    }
}
