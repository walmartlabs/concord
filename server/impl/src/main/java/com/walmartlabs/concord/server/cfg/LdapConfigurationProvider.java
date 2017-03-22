package com.walmartlabs.concord.server.cfg;

import com.google.common.base.Throwables;

import javax.inject.Named;
import javax.inject.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

@Named
public class LdapConfigurationProvider implements Provider<LdapConfiguration> {

    public static final String LDAP_CFG_KEY = "LDAP_CFG";

    @Override
    public LdapConfiguration get() {
        Properties props = new Properties();

        try {
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
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        return new LdapConfiguration(props.getProperty("url"),
                props.getProperty("searchBase"),
                props.getProperty("principalSuffix"),
                props.getProperty("systemUsername"),
                props.getProperty("systemPassword"));
    }
}
