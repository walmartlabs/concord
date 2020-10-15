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

import java.util.Map;

public class CommandResponse extends Message {

    public enum CommandType {
        CANCEL_JOB
    }

    public static CommandResponse cancel(long correlationId, Map<String, Object> payload) {
        return new CommandResponse(correlationId, CommandType.CANCEL_JOB, payload);
    }

    private final CommandType type;

    private final Map<String, Object> payload;

    @JsonCreator
    public CommandResponse(
            @JsonProperty("correlationId") long correlationId,
            @JsonProperty("type") CommandType type,
            @JsonProperty("payload") Map<String, Object> payload) {
        super(MessageType.COMMAND_RESPONSE);
        setCorrelationId(correlationId);
        this.type = type;
        this.payload = payload;
    }

    public CommandType getType() {
        return type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "CommandRequest{" +
                "correlationId='" + getCorrelationId() + "', " +
                "type='" + type + "', " +
                "payload='" + payload + "'" +
                '}';
    }
}
