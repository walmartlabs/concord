package com.walmartlabs.concord.server.security.ldap;

import com.walmartlabs.concord.server.cfg.LdapGroupSyncConfiguration;
import com.walmartlabs.concord.server.user.UserManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class UserLdapGroupSynchronizerTest {

    private UserLdapGroupSynchronizer userLdapGroupSynchronizer;

    @BeforeEach
    public void init() {
        userLdapGroupSynchronizer = new UserLdapGroupSynchronizer(mock(UserLdapGroupSynchronizer.Dao.class),
                mock(LdapGroupSyncConfiguration.class),
                mock(LdapManager.class),
                mock(LdapGroupDao.class),
                mock(UserManager.class));
    }

    @Test
    void checkOrgOwnerDiscrepanciesDiffUsersOnlyInLdap() {
        UserLdapGroupSynchronizer.UserItem user1 = new UserLdapGroupSynchronizer.UserItem(UUID.randomUUID(), "admin", "domain", false);
        UserLdapGroupSynchronizer.UserItem user2 = new UserLdapGroupSynchronizer.UserItem(UUID.randomUUID(), "testUser", null, false);

        List<UserLdapGroupSynchronizer.UserItem> ldapGroupUsers = new ArrayList<>();
        ldapGroupUsers.add(user1);
        ldapGroupUsers.add(user2);

        List<UserLdapGroupSynchronizer.UserItem> ownerRoleUsers = new ArrayList<>();
        ownerRoleUsers.add(user1);

        List<UserLdapGroupSynchronizer.UserItem> usersOnlyInLdapGroup = new ArrayList<>();
        usersOnlyInLdapGroup.add(user2);

        Tuple2<List, List> values = userLdapGroupSynchronizer.checkOrgOwnerDiscrepancies(ldapGroupUsers, ownerRoleUsers);
        assertTrue(values._1().equals(usersOnlyInLdapGroup));
        assertTrue(values._2().isEmpty());
    }

    @Test
    void checkOrgOwnerDiscrepanciesDiffOwnersNotInLdap() {
        UserLdapGroupSynchronizer.UserItem user1 = new UserLdapGroupSynchronizer.UserItem(UUID.randomUUID(), "admin", "domain", false);
        UserLdapGroupSynchronizer.UserItem user2 = new UserLdapGroupSynchronizer.UserItem(UUID.randomUUID(), "testUser", null, false);

        List<UserLdapGroupSynchronizer.UserItem> ldapGroupUsers = new ArrayList<>();
        ldapGroupUsers.add(user1);

        List<UserLdapGroupSynchronizer.UserItem> ownerRoleUsers = new ArrayList<>();
        ownerRoleUsers.add(user1);
        ownerRoleUsers.add(user2);

        List<UserLdapGroupSynchronizer.UserItem> ownersNotInLdapGroup = new ArrayList<>();
        ownersNotInLdapGroup.add(user2);

        Tuple2<List, List> values = userLdapGroupSynchronizer.checkOrgOwnerDiscrepancies(ldapGroupUsers, ownerRoleUsers);
        assertTrue(values._1().isEmpty());
        assertTrue(values._2().equals(ownersNotInLdapGroup));
    }

    @Test
    void checkOrgOwnerDiscrepanciesNoDiff() {
        UserLdapGroupSynchronizer.UserItem user1 = new UserLdapGroupSynchronizer.UserItem(UUID.randomUUID(), "admin", "domain", false);
        UserLdapGroupSynchronizer.UserItem user2 = new UserLdapGroupSynchronizer.UserItem(UUID.randomUUID(), "testUser", null, false);

        List<UserLdapGroupSynchronizer.UserItem> ldapGroupUsers = new ArrayList<>();
        ldapGroupUsers.add(user1);
        ldapGroupUsers.add(user2);

        List<UserLdapGroupSynchronizer.UserItem> ownerRoleUsers = new ArrayList<>();
        ownerRoleUsers.add(user1);
        ownerRoleUsers.add(user2);

        Tuple2<List, List> values = userLdapGroupSynchronizer.checkOrgOwnerDiscrepancies(ldapGroupUsers, ownerRoleUsers);
        assertTrue(values._1().isEmpty());
        assertTrue(values._2().isEmpty());
    }
}