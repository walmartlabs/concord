package com.walmartlabs.concord.server.sdk.audit;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

public class AuditEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String object;
    private final String action;
    private final Map<String, Object> details;

    public AuditEvent(UUID userId, String object, String action, Map<String, Object> details) {
        this.userId = userId;
        this.object = object;
        this.action = action;
        this.details = details;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getObject() {
        return object;
    }

    public String getAction() {
        return action;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
