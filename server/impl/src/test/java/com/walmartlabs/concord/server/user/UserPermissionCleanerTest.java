package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.common.secret.SecretStoreType;
import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.security.secret.SecretType;
import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.project.RepositoryDao;
import com.walmartlabs.concord.server.security.secret.SecretDao;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

@Ignore("requires a local DB instance")
public class UserPermissionCleanerTest extends AbstractDaoTest {

    @Test
    public void testSecrets() throws Exception {
        UserDao userDao = new UserDao(getConfiguration());
        UserPermissionCleaner permissionCleaner = new UserPermissionCleaner(getConfiguration());
        SecretDao secretDao = new SecretDao(getConfiguration(), permissionCleaner);

        // ---

        String secretName = "secret#" + System.currentTimeMillis();
        UUID secretId = secretDao.insert(secretName, SecretType.KEY_PAIR, SecretStoreType.SERVER_KEY, new byte[]{0, 1, 2});

        UUID userId = UUID.randomUUID();
        String username = "user#" + System.currentTimeMillis();
        Set<String> permissions = Collections.singleton(String.format(Permissions.SECRET_READ_INSTANCE, secretName));
        userDao.insert(userId, username, permissions);

        // ---

        secretDao.delete(secretId);
        UserEntry u = userDao.get(userId);
        assertNotNull(u);
        assertTrue(u.getPermissions().isEmpty());
    }

    @Test
    public void testProjectRepositories() throws Exception {
        UserDao userDao = new UserDao(getConfiguration());
        UserPermissionCleaner permissionCleaner = new UserPermissionCleaner(getConfiguration());
        ProjectDao projectDao = new ProjectDao(getConfiguration(), permissionCleaner);
        RepositoryDao repositoryDao = new RepositoryDao(getConfiguration());

        // ---

        String projectName = "project#" + System.currentTimeMillis();
        UUID projectId = projectDao.insert(projectName, "test", null);

        // ---

        String repoName1 = "repo1#" + System.currentTimeMillis();
        repositoryDao.insert(projectId, repoName1, "n/a", null, null, null, null);

        String repoName2 = "repo2#" + System.currentTimeMillis();
        repositoryDao.insert(projectId, repoName2, "n/a", null, null, null, null);

        // ---

        UUID userId = UUID.randomUUID();
        String username = "user#" + System.currentTimeMillis();
        Set<String> perms = new HashSet<>();
        perms.add(String.format(Permissions.PROJECT_UPDATE_INSTANCE, projectName));
        perms.add(Permissions.APIKEY_DELETE_ANY);
        userDao.insert(userId, username, perms);

        // ---

        projectDao.delete(projectId);

        // ---

        UserEntry u = userDao.get(userId);
        assertNotNull(u);
        assertEquals(1, u.getPermissions().size());
        assertEquals(Permissions.APIKEY_DELETE_ANY, u.getPermissions().iterator().next());
    }
}
