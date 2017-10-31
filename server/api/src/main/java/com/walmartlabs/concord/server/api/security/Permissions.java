package com.walmartlabs.concord.server.api.security;

public final class Permissions {

    public static final String PROCESS_START_PROJECT = "process:start:%s";

    public static final String TEMPLATE_PREFIX = "template";
    public static final String TEMPLATE_MANAGE = TEMPLATE_PREFIX + ":manage";

    public static final String SECRET_PREFIX = "secret";
    public static final String SECRET_CREATE_NEW = SECRET_PREFIX + ":create";
    public static final String SECRET_READ_INSTANCE = SECRET_PREFIX + ":read:%s";
    public static final String SECRET_DELETE_INSTANCE = SECRET_PREFIX + ":delete:%s";

    public static final String USER_CREATE_NEW = "user:create";
    public static final String USER_UPDATE_ANY = "user:update";
    public static final String USER_DELETE_ANY = "user:delete";

    public static final String ROLE_CREATE_NEW = "role:create";
    public static final String ROLE_UPDATE_ANY = "role:update";
    public static final String ROLE_DELETE_ANY = "role:delete";

    public static final String LDAP_MAPPING_CREATE_NEW = "ldapMapping:create";
    public static final String LDAP_MAPPING_UPDATE_ANY = "ldapMapping:update";
    public static final String LDAP_MAPPING_DELETE_ANY = "ldapMapping:delete";

    public static final String LDAP_QUERY = "ldap:query";

    private Permissions() {
    }
}
