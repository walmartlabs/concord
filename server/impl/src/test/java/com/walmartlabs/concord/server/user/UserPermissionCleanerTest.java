package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.security.secret.SecretType;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.project.RepositoryDao;
import com.walmartlabs.concord.server.security.User;
import com.walmartlabs.concord.server.security.secret.SecretDao;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

public class UserPermissionCleanerTest extends AbstractDaoTest {

    @Test
    public void testSecrets() throws Exception {
        UserDao userDao = new UserDao(getConfiguration());
        UserPermissionCleaner permissionCleaner = new UserPermissionCleaner(getConfiguration());
        SecretDao secretDao = new SecretDao(getConfiguration(), permissionCleaner);

        // ---

        String secretId = UUID.randomUUID().toString();
        String secretName = "secret#" + System.currentTimeMillis();
        secretDao.insert(secretId, secretName, SecretType.KEY_PAIR, new byte[]{0, 1, 2});

        String userId = UUID.randomUUID().toString();
        String username = "user#" + System.currentTimeMillis();
        Set<String> permissions = Collections.singleton(String.format(Permissions.SECRET_READ_INSTANCE, secretName));
        userDao.insert(userId, username, permissions);

        // ---

        secretDao.delete(secretId);
        User u = userDao.get(userId);
        assertNotNull(u);
        assertTrue(u.getPermissions().isEmpty());
    }

    @Test
    public void testProjectRepositories() throws Exception {
        UserDao userDao = new UserDao(getConfiguration());
        UserPermissionCleaner permissionCleaner = new UserPermissionCleaner(getConfiguration());
        ProjectDao projectDao = new ProjectDao(getConfiguration(), permissionCleaner);
        RepositoryDao repositoryDao = new RepositoryDao(getConfiguration(), permissionCleaner);

        // ---

        String projectId = UUID.randomUUID().toString();
        String projectName = "project#" + System.currentTimeMillis();
        projectDao.insert(projectId, projectName, null);

        // ---

        String repoName1 = "repo1#" + System.currentTimeMillis();
        repositoryDao.insert(projectId, repoName1, "n/a", null, null);

        String repoName2 = "repo2#" + System.currentTimeMillis();
        repositoryDao.insert(projectId, repoName2, "n/a", null, null);

        // ---

        String userId = UUID.randomUUID().toString();
        String username = "user#" + System.currentTimeMillis();
        Set<String> perms = new HashSet<>();
        perms.add(String.format(Permissions.PROJECT_UPDATE_INSTANCE, projectName));
        perms.add(String.format(Permissions.REPOSITORY_UPDATE_INSTANCE, repoName1));
        perms.add(String.format(Permissions.REPOSITORY_UPDATE_INSTANCE, repoName2));
        perms.add(Permissions.APIKEY_DELETE_ANY);
        userDao.insert(userId, username, perms);

        // ---

        projectDao.delete(projectId);

        // ---

        User u = userDao.get(userId);
        assertNotNull(u);
        assertEquals(1, u.getPermissions().size());
        assertEquals(Permissions.APIKEY_DELETE_ANY, u.getPermissions().iterator().next());
    }
}
