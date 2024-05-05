package com.walmartlabs.concord.server.sdk.security;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.server.sdk.ConcordApplicationException;

import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;

public class AuthenticationException extends ConcordApplicationException {

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause, UNAUTHORIZED);
    }

    public AuthenticationException(String message) {
        super(message, UNAUTHORIZED);
    }
}
