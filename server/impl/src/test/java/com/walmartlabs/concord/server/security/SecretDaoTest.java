package com.walmartlabs.concord.server.security;

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.security.secret.SecretType;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.project.RepositoryDao;
import com.walmartlabs.concord.server.security.secret.SecretDao;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class SecretDaoTest extends AbstractDaoTest {

    @Test
    public void testOnCascade() {
        String projectId = UUID.randomUUID().toString();
        String projectName = "project#" + System.currentTimeMillis();

        ProjectDao projectDao = new ProjectDao(getConfiguration(), mock(UserPermissionCleaner.class));
        projectDao.insert(projectId, projectName, Collections.emptySet());

        String secretId = UUID.randomUUID().toString();
        String secretName = "secret#" + System.currentTimeMillis();
        SecretDao secretDao = new SecretDao(getConfiguration(), mock(UserPermissionCleaner.class));
        secretDao.insert(secretId, secretName, SecretType.KEY_PAIR, new byte[]{0, 1, 2});

        String repoName = "repo#" + System.currentTimeMillis();
        RepositoryDao repositoryDao = new RepositoryDao(getConfiguration());
        repositoryDao.insert(projectId, repoName, "n/a", null, secretId);

        // ---

        secretDao.delete(secretId);

        // ---

        RepositoryEntry r = repositoryDao.get(projectId, repoName);
        assertNotNull(r);
        assertNull(r.getSecret());
    }
}
