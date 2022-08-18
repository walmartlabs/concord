package com.walmartlabs.concord.server.process.form;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

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
        runAsParams.put("ldap", new HashMap<>(Collections.singletonMap("group", "ENTINVENTORY")));

        Set<String> groups = new HashSet<>();
        groups.add("CN=EntInventory,OU=Global,OU=Security,OU=Groups,DC=homeoffice,DC=Wal-Mart,DC=com");
        runWithGroups(groups, () -> fam.assertFormAccess("formName", runAsParams));
    }

    private static void runWithGroups(Set<String> groups, Runnable r) throws Exception {
        ThreadState subjectThreadState = null;
        try {
            ThreadContext.bind(mock(SecurityManager.class));

            SimplePrincipalCollection principals = new SimplePrincipalCollection();
            principals.add(new UserPrincipal(InternalRealm.REALM_NAME, new UserEntry(null, "test", null, null, null, null, null, null, false)), InternalRealm.REALM_NAME);
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
