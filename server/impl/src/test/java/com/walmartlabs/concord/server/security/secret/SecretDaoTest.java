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

import com.walmartlabs.concord.common.secret.HashAlgorithm;
import com.walmartlabs.concord.common.secret.SecretEncryptedByType;
import com.walmartlabs.concord.common.secret.SecretUtils;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.walmartlabs.concord.server.org.secret.SecretDao.InsertMode.INSERT;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Disabled("requires a local DB instance")
public class SecretDaoTest extends AbstractDaoTest {

    @Test
    public void testOnCascade() {
        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;

        String projectName = "project#" + System.currentTimeMillis();

        ProjectDao projectDao = new ProjectDao(getConfiguration(), new ConcordObjectMapper(TestObjectMapper.INSTANCE), getUuidGenerator());
        UUID projectId = projectDao.insert(orgId, projectName, "test", null, null, null, null, new byte[0], null, null, null);

        String secretName = "secret#" + System.currentTimeMillis();
        SecretDao secretDao = new SecretDao(getConfiguration(), getUuidGenerator());
        byte[] secretSalt = SecretUtils.generateSalt(16);
        UUID secretId = secretDao.insert(orgId, secretName, null, SecretType.KEY_PAIR, SecretEncryptedByType.SERVER_KEY, "concord", SecretVisibility.PUBLIC, secretSalt, HashAlgorithm.SHA256, INSERT);
        secretDao.updateData(secretId, new byte[]{0, 1, 2});
        secretDao.update(secretId, secretName, UUID.fromString("4b9d496a-c3a0-4e1b-804c-ac3fccddcb27"), null, new byte[0], null, orgId, HashAlgorithm.SHA256);


        String repoName = "repo#" + System.currentTimeMillis();
        RepositoryDao repositoryDao = new RepositoryDao(getConfiguration(), new ConcordObjectMapper(TestObjectMapper.INSTANCE), getUuidGenerator());
        UUID repoId = repositoryDao.insert(projectId, repoName, "n/a", null, null, null, secretId, false, null, false);

        // ---

        secretDao.delete(secretId);

        // ---

        RepositoryEntry r = repositoryDao.get(projectId, repoId);
        assertNotNull(r);
        assertNull(r.getSecretName());
    }
}
