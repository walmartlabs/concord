package com.walmartlabs.concord.server.queueclient.message;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

public enum MessageType {

    COMMAND_REQUEST(CommandRequest.class),
    COMMAND_RESPONSE(CommandResponse.class),
    PROCESS_REQUEST(ProcessRequest.class),
    PROCESS_RESPONSE(ProcessResponse.class);

    private final Class<? extends Message> clazz;

    MessageType(Class<? extends Message> clazz) {
        this.clazz = clazz;
    }

    public Class<? extends Message> getClazz() {
        return clazz;
    }
}
