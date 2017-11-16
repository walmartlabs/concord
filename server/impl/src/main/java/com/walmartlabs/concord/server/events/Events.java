package com.walmartlabs.concord.server.events;

import java.util.HashMap;
import java.util.Map;

public class Events {

    public static final String CONCORD_EVENT = "concord";

    public static class Repository {

        public static final String REPOSITORY_CREATED_EVENT = "repositoryCreated";

        public static final String REPOSITORY_UPDATED_EVENT = "repositoryUpdated";

        public static Map<String, Object> repositoryCreated(String project, String repository) {
            return event(REPOSITORY_CREATED_EVENT, project, repository);
        }

        public static Map<String, Object> repositoryUpdated(String project, String repository) {
            return event(REPOSITORY_UPDATED_EVENT, project, repository);
        }

        private static Map<String, Object> event(String eventType, String project, String repository) {
            Map<String, Object> event = new HashMap<>();
            event.put("project", project);
            event.put("repository", repository);
            event.put("event", eventType);
            return event;
        }
    }
}
