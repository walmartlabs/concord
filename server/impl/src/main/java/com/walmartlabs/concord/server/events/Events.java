package com.walmartlabs.concord.server.events;

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
