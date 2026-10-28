package com.walmartlabs.concord.server.security;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.server.sdk.security.AuthorizationSubjectNotFoundException;
import com.walmartlabs.concord.server.sdk.security.AuthorizationUnavailableException;

import javax.servlet.http.HttpServletResponse;

/** Classifies only explicit authorization failures, including failures wrapped by Shiro. */
public final class AuthorizationFailure {
    private AuthorizationFailure() {}

    public static int status(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof AuthorizationUnavailableException) return HttpServletResponse.SC_SERVICE_UNAVAILABLE;
            if (t instanceof AuthorizationSubjectNotFoundException) return HttpServletResponse.SC_FORBIDDEN;
        }
        return 0;
    }
}
