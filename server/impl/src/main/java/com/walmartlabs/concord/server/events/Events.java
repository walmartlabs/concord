package com.walmartlabs.concord.server.events;

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

import com.walmartlabs.concord.sdk.Constants;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.events.github.Constants.*;

public class Events {

    public static final String CONCORD_EVENT = "concord";

    public static class Repository {

        public static final String REPOSITORY_CREATED_EVENT = "repositoryCreated";

        public static final String REPOSITORY_UPDATED_EVENT = "repositoryUpdated";

        public static Map<String, Object> repositoryCreated(UUID projectId, UUID repositoryId, String repositoryName) {
            return event(REPOSITORY_CREATED_EVENT, projectId, repositoryId, repositoryName);
        }

        public static Map<String, Object> repositoryUpdated(UUID projectId, UUID repositoryId, String repositoryName) {
            return event(REPOSITORY_UPDATED_EVENT, projectId, repositoryId, repositoryName);
        }

        private static Map<String, Object> event(String eventType, UUID projectId, UUID repositoryId, String repositoryName) {
            Map<String, Object> repositoryInfo = new HashMap<>();
            repositoryInfo.put(PROJECT_ID_KEY, projectId);
            repositoryInfo.put(REPO_ID_KEY, repositoryId);
            repositoryInfo.put(REPO_NAME_KEY, repositoryName);

            Map<String, Object> event = new HashMap<>();
            event.put(Constants.Trigger.REPOSITORY_INFO, Collections.singletonList(repositoryInfo));
            event.put("event", eventType);
            return event;
        }
    }
}
