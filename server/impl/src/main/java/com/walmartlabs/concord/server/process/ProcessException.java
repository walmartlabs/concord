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
import javax.ws.rs.core.Response.StatusType;

public class ProcessException extends RuntimeException {

    private final PartialProcessKey processKey;
    private final StatusType status;

    public ProcessException(PartialProcessKey processKey, String message) {
        this(processKey, message, Status.INTERNAL_SERVER_ERROR);
    }

    public ProcessException(PartialProcessKey processKey, String message, StatusType status) {
        super(message);
        this.processKey = processKey;
        this.status = status;
    }

    public ProcessException(PartialProcessKey processKey, String message, Throwable cause) {
        this(processKey, message, cause, Status.INTERNAL_SERVER_ERROR);
    }

    public ProcessException(PartialProcessKey processKey, String message, Throwable cause, StatusType status) {
        super(message, cause);
        this.processKey = processKey;
        this.status = status;
    }

    public PartialProcessKey getProcessKey() {
        return processKey;
    }

    public StatusType getStatus() {
        return status;
    }
}
