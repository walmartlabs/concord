package com.walmartlabs.concord.server.security.secret;

import com.walmartlabs.concord.common.secret.SecretStoreType;
import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.team.secret.SecretType;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.project.RepositoryDao;
import com.walmartlabs.concord.server.team.TeamManager;
import com.walmartlabs.concord.server.team.secret.SecretDao;
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
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;

        String projectName = "project#" + System.currentTimeMillis();

        ProjectDao projectDao = new ProjectDao(getConfiguration());
        UUID projectId = projectDao.insert(teamId, projectName, "test", null, null);

        String secretName = "secret#" + System.currentTimeMillis();
        SecretDao secretDao = new SecretDao(getConfiguration(), mock(UserPermissionCleaner.class));
        UUID secretId = secretDao.insert(teamId, secretName, SecretType.KEY_PAIR, SecretStoreType.SERVER_KEY, new byte[]{0, 1, 2});

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
