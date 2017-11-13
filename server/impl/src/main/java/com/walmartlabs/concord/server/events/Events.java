package com.walmartlabs.concord.server.events;

import java.util.HashMap;
import java.util.Map;

public class Events {

    public static final String CONCORD_EVENT = "concord";

    public static class Repository {

        public static final String CREATED_EVENT = "repositoryCreated";

        public static final String UPDATED_EVENT = "repositoryUpdated";

        public static Map<String, Object> createdEvent(String project, String repository) {
            return event(CREATED_EVENT, project, repository);
        }

        public static Map<String, Object> updatedEvent(String project, String repository) {
            return event(UPDATED_EVENT, project, repository);
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
