package com.walmartlabs.concord.server.cfg;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

@Named
@Singleton
public class LdapConfiguration implements Serializable {

    public static final String LDAP_CFG_KEY = "LDAP_CFG";

    private final String url;
    private final String searchBase;
    private final String principalSuffix;
    private final String systemUsername;
    private final String systemPassword;

    public LdapConfiguration() throws IOException {
        Properties props = new Properties();

        String path = System.getenv(LDAP_CFG_KEY);
        if (path != null) {
            try (InputStream in = Files.newInputStream(Paths.get(path))) {
                props.load(in);
            }
        } else {
            try (InputStream in = LdapConfiguration.class.getResourceAsStream("default_ldap.properties")) {
                props.load(in);
            }
        }

        this.url = props.getProperty("url");
        this.searchBase = props.getProperty("searchBase");
        this.principalSuffix = props.getProperty("principalSuffix");
        this.systemUsername = props.getProperty("systemUsername");
        this.systemPassword = props.getProperty("systemPassword");
    }

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
