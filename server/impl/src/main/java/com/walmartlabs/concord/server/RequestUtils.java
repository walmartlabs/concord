package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import java.util.Map;
import java.util.UUID;

public final class RequestUtils {

    public static final String UI_REQUEST_HEADER = "X-Concord-UI-Request";

    /**
     * Returns the current request's unique ID. The ID is assigned when
     * the request is first received.
     */
    public static UUID getRequestId() {
        RequestContext ctx = RequestContext.get();
        if (ctx == null) {
            return null;
        }

        return ctx.getRequestId();
    }

    /**
     * The UI sends a special header with all requests.
     * The method returns {@code true} if the current request has the header.
     */
    public static boolean isItAUIRequest() {
        RequestContext ctx = RequestContext.get();
        if (ctx == null) {
            return false;
        }

        Map<String, String> headers = ctx.getExtraHeaders();
        if (headers == null || headers.isEmpty()) {
            return false;
        }

        return headers.containsKey(UI_REQUEST_HEADER);
    }

    private RequestUtils() {
    }
}
