package com.walmartlabs.concord.server.process;

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


import javax.ws.rs.core.Response.Status;
import java.util.UUID;

public class ProcessException extends RuntimeException {

    private final UUID instanceId;
    private final Status status;

    public ProcessException(UUID instanceId, String message) {
        this(instanceId, message, Status.INTERNAL_SERVER_ERROR);
    }

    public ProcessException(UUID instanceId, String message, Status status) {
        super(message);
        this.instanceId = instanceId;
        this.status = status;
    }

    public ProcessException(UUID instanceId, String message, Throwable cause) {
        this(instanceId, message, cause, Status.INTERNAL_SERVER_ERROR);
    }

    public ProcessException(UUID instanceId, String message, Throwable cause, Status status) {
        super(message, cause);
        this.instanceId = instanceId;
        this.status = status;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public Status getStatus() {
        return status;
    }
}
