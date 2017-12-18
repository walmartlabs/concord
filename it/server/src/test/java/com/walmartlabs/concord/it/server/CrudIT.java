package com.walmartlabs.concord.it.server;

import com.googlecode.junittoolbox.ParallelRunner;
import com.sun.org.apache.xml.internal.security.utils.Base64;
import com.walmartlabs.concord.server.api.GenericOperationResultResponse;
import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.org.inventory.*;
import com.walmartlabs.concord.server.api.org.landing.CreateLandingResponse;
import com.walmartlabs.concord.server.api.org.landing.LandingEntry;
import com.walmartlabs.concord.server.api.org.landing.LandingPageResource;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.org.secret.SecretEntry;
import com.walmartlabs.concord.server.api.org.team.TeamEntry;
import com.walmartlabs.concord.server.api.org.team.TeamUserEntry;
import com.walmartlabs.concord.server.api.project.CreateProjectResponse;
import com.walmartlabs.concord.server.api.project.DeleteProjectResponse;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import com.walmartlabs.concord.server.api.security.ldap.CreateLdapMappingRequest;
import com.walmartlabs.concord.server.api.security.ldap.CreateLdapMappingResponse;
import com.walmartlabs.concord.server.api.security.ldap.LdapMappingEntry;
import com.walmartlabs.concord.server.api.security.ldap.LdapResource;
import com.walmartlabs.concord.server.api.user.RoleEntry;
import com.walmartlabs.concord.server.api.user.RoleResource;
import com.walmartlabs.concord.server.org.OrganizationManager;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(ParallelRunner.class)
public class CrudIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testProject() {
        ProjectResource projectResource = proxy(ProjectResource.class);

        String projectName = "project_" + randomString();
        CreateProjectResponse cpr = projectResource.createOrUpdate(new ProjectEntry(projectName));
        assertTrue(cpr.isOk());

        cpr = projectResource.createOrUpdate(new ProjectEntry(projectName));
        assertTrue(cpr.isOk());

        // ---

        ProjectEntry e1 = projectResource.get(projectName);
        assertNotNull(e1);

        CreateProjectResponse cpr2 = projectResource.createOrUpdate(new ProjectEntry(projectName, Collections.emptyMap()));
        assertTrue(cpr2.isOk());

        List<ProjectEntry> l = projectResource.list(null, null, false);
        ProjectEntry e2 = findProject(l, projectName);
        assertNotNull(e2);

        DeleteProjectResponse dpr = projectResource.delete(projectName);
        assertTrue(dpr.isOk());
    }

    @Test(timeout = 30000)
    public void testNonUniqueRepositoryNames() throws Exception {
        String projectName1 = "project1_" + randomString();
        String projectName2 = "project2_" + randomString();

        String repoName = "repo_" + randomString();

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new ProjectEntry(projectName1,
                Collections.singletonMap(repoName, new RepositoryEntry(null, null, repoName, "n/a", null, null, null, null))));

        projectResource.createOrUpdate(new ProjectEntry(projectName2,
                Collections.singletonMap(repoName, new RepositoryEntry(null, null, repoName, "n/a", null, null, null, null))));
    }

    @Test(timeout = 30000)
    public void testLdapMappings() throws Exception {
        String roleA = "roleA_" + randomString();
        String roleB = "roleB_" + randomString();
        String ldapDn = "testDn_" + randomString();

        RoleResource roleResource = proxy(RoleResource.class);
        roleResource.createOrUpdate(new RoleEntry(roleA, "A", "1", "2"));
        roleResource.createOrUpdate(new RoleEntry(roleB, "B", "2", "3"));

        // ---

        LdapResource ldapResource = proxy(LdapResource.class);
        CreateLdapMappingResponse clmr = ldapResource.createOrUpdate(new CreateLdapMappingRequest(ldapDn, roleA, roleB));
        assertEquals(OperationResult.CREATED, clmr.getResult());

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

    @Test(timeout = 30000)
    public void testInventory() throws Exception {
        InventoryResource inventoryResource = proxy(InventoryResource.class);

        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        String inventoryName = "inventory_" + randomString();

        // --- create

        CreateInventoryResponse cir = inventoryResource.createOrUpdate(orgName, new InventoryEntry(inventoryName));
        assertTrue(cir.isOk());
        assertNotNull(cir.getId());

        // --- update

        CreateInventoryResponse uir = inventoryResource.createOrUpdate(orgName, new InventoryEntry(inventoryName));
        assertTrue(uir.isOk());
        assertNotNull(uir.getId());

        // --- get

        InventoryEntry i1 = inventoryResource.get(orgName, inventoryName);
        assertNotNull(i1);
        assertNotNull(i1.getId());
        assertEquals(uir.getId(), i1.getId());
        assertEquals(inventoryName, i1.getName());
        assertEquals(orgName, i1.getOrgName());
        assertNull(i1.getParent());

        // --- delete

        GenericOperationResultResponse dpr = inventoryResource.delete(orgName, inventoryName);
        assertTrue(dpr.getResult() == OperationResult.DELETED);
    }

    @Test(timeout = 30000)
    public void testInventoryData() throws Exception {
        InventoryDataResource resource = proxy(InventoryDataResource.class);

        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        String inventoryName = "inventory_" + randomString();
        String itemPath = "/a";
        Map<String, Object> data = Collections.singletonMap("k", "v");

        InventoryResource inventoryResource = proxy(InventoryResource.class);
        inventoryResource.createOrUpdate(orgName, new InventoryEntry(inventoryName));

        // --- create

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resource.data(orgName, inventoryName, itemPath, data);
        assertNotNull(result);
        assertEquals(Collections.singletonMap("a", data), result);

        // --- get

        @SuppressWarnings("unchecked")
        Map<String, Object> result2 = (Map<String, Object>) resource.get(orgName, inventoryName, itemPath);
        assertNotNull(result2);
        assertEquals(Collections.singletonMap("a", data), result);

        // --- delete

        DeleteInventoryDataResponse didr = resource.delete(orgName, inventoryName, itemPath);
        assertNotNull(didr);
        assertTrue(didr.isOk());
    }

    @Test(timeout = 30000)
    public void testInventoryQuery() throws Exception {
        InventoryQueryResource resource = proxy(InventoryQueryResource.class);

        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        String inventoryName = "inventory_" + randomString();
        String queryName = "queryName_" + randomString();
        String text = "text_" + randomString();
        ;

        InventoryResource inventoryResource = proxy(InventoryResource.class);
        inventoryResource.createOrUpdate(orgName, new InventoryEntry(inventoryName));

        // --- create

        CreateInventoryQueryResponse cqr = resource.createOrUpdate(orgName, inventoryName, queryName, text);
        assertTrue(cqr.isOk());
        assertNotNull(cqr.getId());

        // --- update
        String updatedText = "select cast(json_build_object('k', 'v') as varchar)";
        CreateInventoryQueryResponse uqr = resource.createOrUpdate(orgName, inventoryName, queryName, updatedText);
        assertTrue(uqr.isOk());
        assertNotNull(uqr.getId());

        // --- get
        InventoryQueryEntry e1 = resource.get(orgName, inventoryName, queryName);
        assertNotNull(e1);
        assertNotNull(e1.getId());
        assertEquals(inventoryName, e1.getInventoryName());
        assertEquals(queryName, e1.getName());
        assertEquals(updatedText, e1.getText());

        // --- exec
        @SuppressWarnings("unchecked")
        List<Object> result = resource.exec(orgName, inventoryName, queryName, null);
        assertNotNull(result);
        Map<String, Object> m = (Map<String, Object>) result.get(0);
        assertEquals(Collections.singletonMap("k", "v"), m);

        // --- delete
        DeleteInventoryQueryResponse dqr = resource.delete(orgName, inventoryName, queryName);
        assertNotNull(dqr);
        assertTrue(dqr.isOk());
    }

    @Test(timeout = 30000)
    public void testLanding() throws Exception {
        ProjectResource projectResource = proxy(ProjectResource.class);
        LandingPageResource resource = proxy(LandingPageResource.class);

        String projectName = "project_" + randomString();
        String repositoryName = "repository_" + randomString();
        String name = "lp-name-1";
        String description = "description";
        String icon = Base64.encode("icon".getBytes());

        projectResource.createOrUpdate(new ProjectEntry(projectName,
                Collections.singletonMap(repositoryName,
                        new RepositoryEntry(null, null, repositoryName, "http://localhost", null, null, null, null))));

        // --- create
        LandingEntry entry = new LandingEntry(null, null, null, null, projectName, repositoryName, name, description, icon);
        CreateLandingResponse result = resource.createOrUpdate(OrganizationManager.DEFAULT_ORG_NAME, entry);
        assertNotNull(result);
        assertTrue(result.isOk());
        assertNotNull(result.getId());
        assertEquals(OperationResult.CREATED, result.getResult());

        // --- update
        result = resource.createOrUpdate(OrganizationManager.DEFAULT_ORG_NAME, new LandingEntry(result.getId(), null, null, null, projectName, repositoryName, name, description, icon));
        assertNotNull(result);
        assertTrue(result.isOk());
        assertNotNull(result.getId());
        assertEquals(OperationResult.UPDATED, result.getResult());

        // --- list
        List<LandingEntry> listResult = resource.list(OrganizationManager.DEFAULT_ORG_NAME);
        assertNotNull(listResult);
    }

    private static ProjectEntry findProject(List<ProjectEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }

    private static RepositoryEntry findRepository(List<RepositoryEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().orElse(null);
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
