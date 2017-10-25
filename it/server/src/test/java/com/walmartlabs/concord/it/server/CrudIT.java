package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.PerformedActionType;
import com.walmartlabs.concord.server.api.inventory.*;
import com.walmartlabs.concord.server.api.project.*;
import com.walmartlabs.concord.server.api.security.ldap.CreateLdapMappingRequest;
import com.walmartlabs.concord.server.api.security.ldap.CreateLdapMappingResponse;
import com.walmartlabs.concord.server.api.security.ldap.LdapMappingEntry;
import com.walmartlabs.concord.server.api.security.ldap.LdapResource;
import com.walmartlabs.concord.server.api.security.secret.*;
import com.walmartlabs.concord.server.api.team.*;
import com.walmartlabs.concord.server.api.user.*;
import org.junit.Test;

import javax.ws.rs.BadRequestException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.server.team.TeamDao.DEFAULT_TEAM_ID;
import static org.junit.Assert.*;

public class CrudIT extends AbstractServerIT {

    @Test
    public void testTeams() {
        TeamResource teamResource = proxy(TeamResource.class);

        String teamName = "team_" + System.currentTimeMillis();
        CreateTeamResponse ctr = teamResource.createOrUpdate(new TeamEntry(null, teamName, null, null, null));
        assertNotNull(ctr.getId());

        TeamEntry te = teamResource.get(teamName);
        assertNotNull(te);
        assertEquals(ctr.getId(), te.getId());

        List<TeamEntry> list = teamResource.list();
        te = findTeam(list, teamName);
        assertNotNull(te);
        assertEquals(ctr.getId(), te.getId());

        // ---

        UserResource userResource = proxy(UserResource.class);

        String userA = "userA_" + System.currentTimeMillis();
        CreateUserResponse curA = userResource.createOrUpdate(new CreateUserRequest(userA, null));


        String userB = "userB_" + System.currentTimeMillis();
        CreateUserResponse curB = userResource.createOrUpdate(new CreateUserRequest(userB, null));

        // ---

        teamResource.addUsers(teamName, Arrays.asList(
                new TeamUserEntry(userA, TeamRole.READER),
                new TeamUserEntry(userB, TeamRole.READER)));

        List<TeamUserEntry> teamUserEntries = teamResource.listUsers(teamName);
        TeamUserEntry entryA = findTeamUser(teamUserEntries, userA);
        assertEquals(curA.getId(), entryA.getId());

        TeamUserEntry entryB = findTeamUser(teamUserEntries, userB);
        assertEquals(curB.getId(), entryB.getId());

        // ---

        teamResource.removeUsers(teamName, Arrays.asList(userA));

        teamUserEntries = teamResource.listUsers(teamName);
        entryA = findTeamUser(teamUserEntries, userA);
        assertNull(entryA);
    }

    @Test
    public void testProject() {
        ProjectResource projectResource = proxy(ProjectResource.class);

        String projectName = "project_" + System.currentTimeMillis();
        CreateProjectResponse cpr = projectResource.createOrUpdate(new ProjectEntry(null, projectName, null, null, null, null, null));
        assertTrue(cpr.isOk());

        cpr = projectResource.createOrUpdate(new ProjectEntry(null, projectName, null, null, null, null, null));
        assertTrue(cpr.isOk());

        // ---

        ProjectEntry e1 = projectResource.get(projectName);
        assertNotNull(e1);

        UpdateProjectResponse upr = projectResource.update(projectName, new UpdateProjectRequest(null, null, null, null, Collections.emptyMap()));
        assertTrue(upr.isOk());

        List<ProjectEntry> l = projectResource.list(null, false);
        ProjectEntry e2 = findProject(l, projectName);
        assertNotNull(e2);

        DeleteProjectResponse dpr = projectResource.delete(projectName);
        assertTrue(dpr.isOk());
    }

    @Test
    public void testRepository() throws Exception {
        String projectName = "project_" + System.currentTimeMillis();
        String repoName = "repo_" + System.currentTimeMillis();
        String branch = "branch_" + System.currentTimeMillis();
        String commitId = "commitId_" + System.currentTimeMillis();

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new ProjectEntry(null, projectName, null, null, null,
                Collections.singletonMap(repoName, new UpdateRepositoryRequest("n/a", branch, null, null, null)), null));

        // ---

        UpdateRepositoryResponse urr = projectResource.updateRepository(projectName, repoName, new UpdateRepositoryRequest("something", branch, commitId, null, null));
        assertTrue(urr.isOk());

        // ---

        List<RepositoryEntry> l = projectResource.listRepositories(projectName, null, true);
        RepositoryEntry e = findRepository(l, repoName);
        assertNotNull(e);
        assertEquals("something", e.getUrl());
        assertEquals(branch, e.getBranch());
        assertEquals(commitId, e.getCommitId());

        DeleteRepositoryResponse drr = projectResource.deleteRepository(projectName, repoName);
        assertTrue(drr.isOk());
    }

    @Test
    public void testNonUniqueRepositoryNames() throws Exception {
        String projectName1 = "project1_" + System.currentTimeMillis();
        String projectName2 = "project2_" + System.currentTimeMillis();

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new ProjectEntry(null, projectName1, null, null, null, null, null));
        projectResource.createOrUpdate(new ProjectEntry(null, projectName2, null, null, null, null, null));

        // ---

        String repoName = "repo_" + System.currentTimeMillis();
        CreateRepositoryResponse crr1 = projectResource.createRepository(projectName1, new CreateRepositoryRequest(repoName, "n/a", null, null, null, null));
        assertTrue(crr1.isOk());

        CreateRepositoryResponse crr2 = projectResource.createRepository(projectName2, new CreateRepositoryRequest(repoName, "n/a", null, null, null, null));
        assertTrue(crr2.isOk());
    }

    @Test
    public void testSecretKeyPair() throws Exception {
        String keyName = "key_" + System.currentTimeMillis();
        SecretResource secretResource = proxy(SecretResource.class);

        // ---

        PublicKeyResponse pkr = secretResource.createKeyPair(keyName, null, null);
        assertTrue(pkr.isOk());
        assertNotNull(pkr.getPublicKey());

        // ---

        PublicKeyResponse pkr2 = secretResource.getPublicKey(keyName);
        assertEquals(pkr.getPublicKey(), pkr2.getPublicKey());

        // ---

        List<SecretEntry> l = secretResource.list(null, true);
        SecretEntry s = findSecret(l, keyName);
        assertNotNull(s);
        assertEquals(keyName, s.getName());

        // ---

        secretResource.delete(keyName);

        // ---

        try {
            secretResource.getPublicKey(keyName);
            fail("should fail");
        } catch (BadRequestException e) {
        }
    }

    @Test
    public void testSecretUsernamePassword() throws Exception {
        String keyName = "key_" + System.currentTimeMillis();
        SecretResource secretResource = proxy(SecretResource.class);

        // ---

        UploadSecretResponse usr = secretResource.addUsernamePassword(keyName, null, null,
                new UsernamePasswordRequest("something", new char[]{'a', 'b', 'c'}));
        assertTrue(usr.isOk());

        // ---

        List<SecretEntry> l = secretResource.list(null, true);
        SecretEntry s = findSecret(l, keyName);
        assertNotNull(s);
        assertEquals(SecretType.USERNAME_PASSWORD, s.getType());
        assertEquals(keyName, s.getName());
    }

    @Test
    public void testLdapMappings() throws Exception {
        String roleA = "roleA_" + System.currentTimeMillis();
        String roleB = "roleB_" + System.currentTimeMillis();
        String ldapDn = "testDn_" + System.currentTimeMillis();

        RoleResource roleResource = proxy(RoleResource.class);
        roleResource.createOrUpdate(new RoleEntry(roleA, "A", "1", "2"));
        roleResource.createOrUpdate(new RoleEntry(roleB, "B", "2", "3"));

        // ---

        LdapResource ldapResource = proxy(LdapResource.class);
        CreateLdapMappingResponse clmr = ldapResource.createOrUpdate(new CreateLdapMappingRequest(ldapDn, roleA, roleB));
        assertEquals(PerformedActionType.CREATED, clmr.getActionType());

        List<LdapMappingEntry> l = ldapResource.listMappings();
        assertFalse(l.isEmpty());

        boolean found = false;
        for (LdapMappingEntry e : l) {
            if (e.getId().equals(clmr.getId())) {
                found = true;
                break;
            }
        }
        assertTrue(found);

        ldapResource.deleteMapping(clmr.getId());
    }

    @Test
    public void testInventory() throws Exception {
        InventoryResource inventoryResource = proxy(InventoryResource.class);

        String inventoryName = "inventory_" + System.currentTimeMillis();
        String teamName = "Default";

        // --- create

        CreateInventoryResponse cir = inventoryResource.createOrUpdate(new InventoryEntry(null, inventoryName, null, teamName, null));
        assertTrue(cir.isOk());
        assertNotNull(cir.getId());

        // --- update

        CreateInventoryResponse uir = inventoryResource.createOrUpdate(new InventoryEntry(null, inventoryName, DEFAULT_TEAM_ID, null, null));
        assertTrue(uir.isOk());
        assertNotNull(uir.getId());

        // --- get

        InventoryEntry i1 = inventoryResource.get(inventoryName);
        assertNotNull(i1);
        assertNotNull(i1.getId());
        assertEquals(uir.getId(), i1.getId());
        assertEquals(inventoryName, i1.getName());
        assertEquals(teamName, i1.getTeamName());
        assertNull(i1.getParent());

        // --- delete

        DeleteInventoryResponse dpr = inventoryResource.delete(inventoryName);
        assertTrue(dpr.isOk());
    }

    @Test
    public void testInventoryData() throws Exception {
        InventoryDataResource resource = proxy(InventoryDataResource.class);

        String inventoryName = "inventory_" + System.currentTimeMillis();
        String itemPath = "/a";
        Map<String, Object> data = Collections.singletonMap("k", "v");

        InventoryResource inventoryResource = proxy(InventoryResource.class);
        inventoryResource.createOrUpdate(new InventoryEntry(null, inventoryName, null, null,null));

        // --- create

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resource.data(inventoryName, itemPath, data);
        assertNotNull(result);
        assertEquals(Collections.singletonMap("a", data), result);

        // --- get

        @SuppressWarnings("unchecked")
        Map<String, Object> result2 = (Map<String, Object>) resource.get(inventoryName, itemPath);
        assertNotNull(result2);
        assertEquals(Collections.singletonMap("a", data), result);

        // --- delete

        DeleteInventoryDataResponse didr = resource.delete(inventoryName, itemPath);
        assertNotNull(didr);
        assertTrue(didr.isOk());
    }

    @Test
    public void testInventoryQuery() throws Exception {
        InventoryQueryResource resource = proxy(InventoryQueryResource.class);

        String inventoryName = "inventory_" + System.currentTimeMillis();
        String queryName = "queryName_" + System.currentTimeMillis();
        String text = "text_" + System.currentTimeMillis();;

        InventoryResource inventoryResource = proxy(InventoryResource.class);
        inventoryResource.createOrUpdate(new InventoryEntry(null, inventoryName, null, null,null));

        // --- create

        CreateInventoryQueryResponse cqr = resource.createOrUpdate(inventoryName, queryName, text);
        assertTrue(cqr.isOk());
        assertNotNull(cqr.getId());

        // --- update
        String updatedText = "select cast(json_build_object('k', 'v') as varchar)";
        CreateInventoryQueryResponse uqr = resource.createOrUpdate(inventoryName, queryName, updatedText);
        assertTrue(uqr.isOk());
        assertNotNull(uqr.getId());

        // --- get
        InventoryQueryEntry e1 = resource.get(inventoryName, queryName);
        assertNotNull(e1);
        assertNotNull(e1.getId());
        assertEquals(inventoryName, e1.getInventoryName());
        assertEquals(queryName, e1.getName());
        assertEquals(updatedText, e1.getText());

        // --- exec
        @SuppressWarnings("unchecked")
        List<Object> result = resource.exec(inventoryName, queryName, null);
        assertNotNull(result);
        Map<String, Object> m = (Map<String, Object>) result.get(0);
        assertEquals(Collections.singletonMap("k", "v"), m);

        // --- delete
        DeleteInventoryQueryResponse dqr = resource.delete(inventoryName, queryName);
        assertNotNull(dqr);
        assertTrue(dqr.isOk());
    }

    private static ProjectEntry findProject(List<ProjectEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }

    private static RepositoryEntry findRepository(List<RepositoryEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }

    private static SecretEntry findSecret(List<SecretEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }

    private static TeamEntry findTeam(List<TeamEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }

    private static TeamUserEntry findTeamUser(List<TeamUserEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getUsername())).findAny().orElse(null);
    }
}
