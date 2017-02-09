package com.walmartlabs.concord.server.api.security;

public final class Permissions {

    public static final String APIKEY_PREFIX = "apikey";
    public static final String APIKEY_CREATE_NEW = APIKEY_PREFIX + ":create";
    public static final String APIKEY_DELETE_ANY = APIKEY_PREFIX + ":delete";

    public static final String PROCESS_START_PROJECT = "process:start:%s";

    public static final String PROJECT_PREFIX = "project";
    public static final String PROJECT_CREATE_NEW = PROJECT_PREFIX + ":create";
    public static final String PROJECT_READ_INSTANCE = PROJECT_PREFIX + ":read:%s";
    public static final String PROJECT_UPDATE_INSTANCE = PROJECT_PREFIX + ":update:%s";
    public static final String PROJECT_DELETE_INSTANCE = PROJECT_PREFIX + ":delete:%s";

    public static final String TEMPLATE_PREFIX = "template";
    public static final String TEMPLATE_CREATE_NEW = TEMPLATE_PREFIX + ":create";
    public static final String TEMPLATE_USE_INSTANCE = TEMPLATE_PREFIX + ":use:%s";

    public static final String SECRET_PREFIX = "secret";
    public static final String SECRET_CREATE_NEW = SECRET_PREFIX + ":create";
    public static final String SECRET_READ_INSTANCE = SECRET_PREFIX + ":read:%s";
    public static final String SECRET_DELETE_INSTANCE = SECRET_PREFIX + ":delete:%s";

    public static final String REPOSITORY_PREFIX = "repository";
    public static final String REPOSITORY_CREATE_NEW = REPOSITORY_PREFIX + ":create";
    public static final String REPOSITORY_READ_INSTANCE = REPOSITORY_PREFIX + ":read:%s";
    public static final String REPOSITORY_UPDATE_INSTANCE = REPOSITORY_PREFIX + ":update:%s";
    public static final String REPOSITORY_DELETE_INSTANCE = REPOSITORY_PREFIX + ":delete:%s";
    
    public static final String USER_CREATE_NEW = "user:create";
    public static final String USER_UPDATE_ANY = "user:update";
    public static final String USER_DELETE_ANY = "user:delete";

    private Permissions() {
    }
}
