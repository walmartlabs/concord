package com.walmartlabs.concord.server.api.security;

public final class Permissions {
    
    public static final String APIKEY_CREATE_NEW = "apikey:create";
    public static final String APIKEY_DELETE_ANY = "apikey:delete";

    public static final String PROCESS_START_PROJECT = "process:start:%s";

    public static final String PROJECT_CREATE_NEW = "project:create";
    public static final String PROJECT_UPDATE_INSTANCE = "project:update:%s";
    public static final String PROJECT_DELETE_INSTANCE = "project:delete:%s";

    public static final String TEMPLATE_CREATE_NEW = "template:create";
    public static final String TEMPLATE_USE_INSTANCE = "template:use:%s";
    
    public static final String REPOSITORY_CREATE_NEW = "repository:create";
    public static final String REPOSITORY_UPDATE_INSTANCE = "repository:update:%s";
    public static final String REPOSITORY_DELETE_INSTANCE = "repository:delete:%s";
    
    public static final String USER_CREATE_NEW = "user:create";
    public static final String USER_UPDATE_ANY = "user:update";
    public static final String USER_DELETE_ANY = "user:delete";

    private Permissions() {
    }
}
