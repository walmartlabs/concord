package com.walmartlabs.concord.server.security.secret;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.common.secret.SecretEncryptedByType;
import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.TestObjectMapper;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.secret.SecretDao;
import com.walmartlabs.concord.server.org.secret.SecretType;
import com.walmartlabs.concord.server.org.secret.SecretVisibility;
import org.junit.Ignore;
import org.junit.Test;

import java.util.UUID;

import static com.walmartlabs.concord.server.org.secret.SecretDao.InsertMode.INSERT;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Ignore("requires a local DB instance")
public class SecretDaoTest extends AbstractDaoTest {

    @Test
    public void testOnCascade() {
        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;

        String projectName = "project#" + System.currentTimeMillis();

        ProjectDao projectDao = new ProjectDao(getConfiguration(), new ConcordObjectMapper(TestObjectMapper.INSTANCE));
        UUID projectId = projectDao.insert(orgId, projectName, "test", null, null, null, null, new byte[0], null);

        String secretName = "secret#" + System.currentTimeMillis();
        SecretDao secretDao = new SecretDao(getConfiguration());
        UUID secretId = secretDao.insert(orgId, null, secretName, null, SecretType.KEY_PAIR, SecretEncryptedByType.SERVER_KEY, "concord", SecretVisibility.PUBLIC, INSERT);
        secretDao.updateData(secretId, new byte[]{0, 1, 2});

        String repoName = "repo#" + System.currentTimeMillis();
        RepositoryDao repositoryDao = new RepositoryDao(getConfiguration(), new ConcordObjectMapper(TestObjectMapper.INSTANCE));
        UUID repoId = repositoryDao.insert(projectId, repoName, "n/a", null, null, null, secretId, false, null);

        // ---

        secretDao.delete(secretId);

        // ---

        RepositoryEntry r = repositoryDao.get(projectId, repoId);
        assertNotNull(r);
        assertNull(r.getSecretName());
    }
}
