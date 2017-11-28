package com.walmartlabs.concord.server.security.secret;

import com.walmartlabs.concord.common.secret.SecretStoreType;
import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.org.secret.SecretType;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.secret.SecretDao;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.junit.Ignore;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

@Ignore("requires a local DB instance")
public class SecretDaoTest extends AbstractDaoTest {

    @Test
    public void testOnCascade() {
        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;

        String projectName = "project#" + System.currentTimeMillis();

        ProjectDao projectDao = new ProjectDao(getConfiguration());
        UUID projectId = projectDao.insert(orgId, projectName, "test", null, null, null);

        String secretName = "secret#" + System.currentTimeMillis();
        SecretDao secretDao = new SecretDao(getConfiguration(), mock(UserPermissionCleaner.class));
        UUID secretId = secretDao.insert(orgId, secretName, null, SecretType.KEY_PAIR, SecretStoreType.SERVER_KEY, null, new byte[]{0, 1, 2});

        String repoName = "repo#" + System.currentTimeMillis();
        RepositoryDao repositoryDao = new RepositoryDao(getConfiguration());
        UUID repoId = repositoryDao.insert(projectId, repoName, "n/a", null, null, null, secretId);

        // ---

        secretDao.delete(secretId);

        // ---

        RepositoryEntry r = repositoryDao.get(projectId, repoId);
        assertNotNull(r);
        assertNull(r.getSecret());
    }
}
