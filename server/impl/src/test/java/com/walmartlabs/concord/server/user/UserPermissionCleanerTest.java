package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.common.secret.SecretStoreType;
import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.security.secret.SecretType;
import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.project.RepositoryDao;
import com.walmartlabs.concord.server.security.secret.SecretDao;
import com.walmartlabs.concord.server.team.TeamManager;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Ignore("requires a local DB instance")
public class UserPermissionCleanerTest extends AbstractDaoTest {

    @Test
    public void testSecrets() throws Exception {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;

        UserDao userDao = new UserDao(getConfiguration());
        UserPermissionCleaner permissionCleaner = new UserPermissionCleaner(getConfiguration());
        SecretDao secretDao = new SecretDao(getConfiguration(), permissionCleaner);

        // ---

        String secretName = "secret#" + System.currentTimeMillis();
        UUID secretId = secretDao.insert(teamId, secretName, SecretType.KEY_PAIR, SecretStoreType.SERVER_KEY, new byte[]{0, 1, 2});

        String username = "user#" + System.currentTimeMillis();
        Set<String> permissions = Collections.singleton(String.format(Permissions.SECRET_READ_INSTANCE, secretName));
        UUID userId = userDao.insert(username, permissions);

        // ---

        secretDao.delete(secretId);
        UserEntry u = userDao.get(userId);
        assertNotNull(u);
        assertTrue(u.getPermissions().isEmpty());
    }

    @Test
    public void testProjectRepositories() throws Exception {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;

        UserDao userDao = new UserDao(getConfiguration());
        ProjectDao projectDao = new ProjectDao(getConfiguration());
        RepositoryDao repositoryDao = new RepositoryDao(getConfiguration());

        // ---

        String projectName = "project#" + System.currentTimeMillis();
        UUID projectId = projectDao.insert(teamId, projectName, "test", null, null);

        // ---

        String repoName1 = "repo1#" + System.currentTimeMillis();
        repositoryDao.insert(projectId, repoName1, "n/a", null, null, null, null);

        String repoName2 = "repo2#" + System.currentTimeMillis();
        repositoryDao.insert(projectId, repoName2, "n/a", null, null, null, null);

        // ---

        String username = "user#" + System.currentTimeMillis();
        Set<String> perms = new HashSet<>();
        UUID userId = userDao.insert(username, perms);

        // ---

        projectDao.delete(projectId);

        // ---

        UserEntry u = userDao.get(userId);
        assertNotNull(u);
    }
}
