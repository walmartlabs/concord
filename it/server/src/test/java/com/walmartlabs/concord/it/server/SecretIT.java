package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.client.*;
import org.junit.Test;

import javax.naming.directory.DirContext;

import static com.walmartlabs.concord.it.server.GitHubTriggersV2IT.createLdapUser;
import static org.junit.Assert.assertNotNull;

public class SecretIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testPublicKey() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName));

        // ---

        String secretName = "secret_" + randomString();
        generateKeyPair(orgName, projectName, secretName, false, null);

        // ---

        String userName = "myUser_" + randomString();
        DirContext ldapCtx = LdapIT.createContext();
        createLdapUser(ldapCtx, userName);

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userName)
                .setType(CreateUserRequest.TypeEnum.LDAP));

        SecretsApi secretsApi = new SecretsApi(getApiClient());
        SecretUpdateRequest req = new SecretUpdateRequest();
        req.setOwner(new EntityOwner()
                .setUsername(userName)
        );
        secretsApi.update(orgName, secretName, req);

        PublicKeyResponse pkr = secretsApi.getPublicKey(orgName, secretName);

        assertNotNull(pkr);
        assertNotNull(pkr.getPublicKey());

        // ---

        secretsApi.delete(orgName, secretName);
        projectsApi.delete(orgName, projectName);
        orgApi.delete(orgName, "yes");

    }
}
