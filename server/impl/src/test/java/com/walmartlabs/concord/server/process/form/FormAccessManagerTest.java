package com.walmartlabs.concord.server.process.form;

import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.internal.InternalRealm;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.support.SubjectThreadState;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.util.ThreadState;
import org.junit.jupiter.api.Test;
import org.apache.shiro.mgt.SecurityManager;

import java.io.Serializable;
import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FormAccessManagerTest {

    @Test
    public void testGroupsMatch() throws Exception {
        FormAccessManager fam = new FormAccessManager(null);
        Map<String, Serializable> runAsParams = new HashMap<>();
        runAsParams.put("ldap", new HashMap<>(Collections.singletonMap("group", "CN=ENTINVENTORY.*")));

        Set<String> groups = new HashSet<>();
        groups.add("CN=EntInventory,OU=Global,OU=Security,OU=Groups,DC=homeoffice,DC=Wal-Mart,DC=com");
        runWithGroups(groups, () -> fam.assertFormAccess("formName", runAsParams));
    }

    private static void runWithGroups(Set<String> groups, Runnable r) throws Exception {
        ThreadState subjectThreadState = null;
        try {
            ThreadContext.bind(mock(SecurityManager.class));

            SimplePrincipalCollection principals = new SimplePrincipalCollection();
            principals.add(new UserPrincipal(InternalRealm.REALM_NAME, new UserEntry(null, null, null, null, null, null, null, null, false)), InternalRealm.REALM_NAME);
            principals.add(new LdapPrincipal("test", "test",
                    null, "test", "test", "mail@test.com", groups, null),
                    "ldap");

            Subject subject = mock(Subject.class);
            when(subject.isAuthenticated()).thenReturn(true);
            when(subject.getPrincipals()).thenReturn(principals);

            subjectThreadState = new SubjectThreadState(subject);
            subjectThreadState.bind();

            r.run();
        } finally {
            if (subjectThreadState != null) {
                subjectThreadState.clear();
            }
            ThreadContext.unbindSecurityManager();
        }
    }
}
