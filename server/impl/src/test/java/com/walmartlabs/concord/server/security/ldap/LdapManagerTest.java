package com.walmartlabs.concord.server.security.ldap;

import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class LdapManagerTest {

    @Test
    public void test() throws Exception {
        LdapConfiguration cfg = new LdapConfiguration();
        LdapManager ldapManager = new LdapManager(cfg);

        LdapInfo info = ldapManager.getInfo("vn03g8w");
        System.out.println(info.getDisplayName());
    }
}
