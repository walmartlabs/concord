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


import java.util.HashMap;
import java.util.Map;

public class Events {

    public static final String CONCORD_EVENT = "concord";

    public static class Repository {

        public static final String REPOSITORY_CREATED_EVENT = "repositoryCreated";

        public static final String REPOSITORY_UPDATED_EVENT = "repositoryUpdated";

        public static Map<String, Object> repositoryCreated(String orgName, String project, String repository) {
            return event(REPOSITORY_CREATED_EVENT, orgName, project, repository);
        }

        public static Map<String, Object> repositoryUpdated(String orgName, String project, String repository) {
            return event(REPOSITORY_UPDATED_EVENT, orgName, project, repository);
        }

        private static Map<String, Object> event(String eventType, String orgName, String project, String repository) {
            Map<String, Object> event = new HashMap<>();
            event.put("org", orgName);
            event.put("project", project);
            event.put("repository", repository);
            event.put("event", eventType);
            return event;
        }
    }
}
