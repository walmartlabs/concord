package com.walmartlabs.concord.server.sdk.events;

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

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;

/**
 * A synchronous, in-process external trigger request. All components are explicit
 * constructor arguments. The supplied maps may be empty or contain null values;
 * their references are retained without copying. Callers must not mutate the maps
 * or nested data during submission, and the server treats them as read-only.
 *
 * @param source event source
 * @param id event identifier
 * @param triggerVersion DAO trigger selection version
 * @param conditions trigger matching input; no version is added implicitly
 * @param attributes workflow-visible event data; no version is added implicitly
 * @param initiatorUsername optional LDAP username, resolved only when a trigger's
 *                          initiator policy requests it; null uses the authenticated caller
 */
public record ExternalEvent(String source,
                            String id,
                            int triggerVersion,
                            Map<String, Object> conditions,
                            Map<String, Object> attributes,
                            @Nullable String initiatorUsername) {

    public ExternalEvent {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(conditions, "conditions");
        Objects.requireNonNull(attributes, "attributes");
    }
}
