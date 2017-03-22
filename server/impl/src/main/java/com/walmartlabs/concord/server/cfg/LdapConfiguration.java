package com.walmartlabs.concord.server.cfg;

import java.io.Serializable;

public class LdapConfiguration implements Serializable {

    private final String url;
    private final String searchBase;
    private final String principalSuffix;
    private final String systemUsername;
    private final String systemPassword;

    public LdapConfiguration(String url, String searchBase, String principalSuffix, String systemUsername, String systemPassword) {
        this.url = url;
        this.searchBase = searchBase;
        this.principalSuffix = principalSuffix;
        this.systemUsername = systemUsername;
        this.systemPassword = systemPassword;
    }

    public String getUrl() {
        return url;
    }

    public String getSearchBase() {
        return searchBase;
    }

    public String getPrincipalSuffix() {
        return principalSuffix;
    }

    public String getSystemUsername() {
        return systemUsername;
    }

    public String getSystemPassword() {
        return systemPassword;
    }
}
