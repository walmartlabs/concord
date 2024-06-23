package com.walmartlabs.concord.server.sdk;

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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Serial;

public class ConcordApplicationException extends WebApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConcordApplicationException(Response resp) {
        super(resp);
    }

    public ConcordApplicationException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public ConcordApplicationException(String message) {
        this(message, (Throwable) null);
    }

    public ConcordApplicationException(String message, Throwable cause) {
        super(message, cause, Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(message)
                .type(MediaType.TEXT_PLAIN_TYPE)
                .build());
    }

    public ConcordApplicationException(String message, Response.Status status) {
        super(message, null, Response.status(status)
                .entity(message)
                .type(MediaType.TEXT_PLAIN_TYPE)
                .build());
    }

    public ConcordApplicationException(String message, Throwable cause, Response.Status status) throws IllegalArgumentException {
        super(message, cause, Response.status(status)
                .entity(message)
                .type(MediaType.TEXT_PLAIN_TYPE)
                .build());
    }
}
