package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.googlecode.junittoolbox.ParallelRunner;
import com.sun.org.apache.xml.internal.security.utils.Base64;
import com.walmartlabs.concord.server.api.GenericOperationResultResponse;
import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.org.CreateOrganizationResponse;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.OrganizationResource;
import com.walmartlabs.concord.server.api.org.inventory.*;
import com.walmartlabs.concord.server.api.org.landing.CreateLandingResponse;
import com.walmartlabs.concord.server.api.org.landing.LandingEntry;
import com.walmartlabs.concord.server.api.org.landing.LandingPageResource;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.org.secret.SecretResource;
import com.walmartlabs.concord.server.api.org.team.CreateTeamResponse;
import com.walmartlabs.concord.server.api.org.team.TeamEntry;
import com.walmartlabs.concord.server.api.org.team.TeamResource;
import com.walmartlabs.concord.server.api.project.CreateProjectResponse;
import com.walmartlabs.concord.server.api.project.DeleteProjectResponse;
import com.walmartlabs.concord.server.api.project.ProjectResource;
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
        String updateProjectName = "updateProject_" + randomString();

        // --- create

        CreateProjectResponse createProjectResponse = projectResource.createOrUpdate(new ProjectEntry(projectName));
        assertTrue(createProjectResponse.isOk());
        assertNotNull(createProjectResponse.getId());

        // --- update

        CreateProjectResponse updateProjectResponse = projectResource.createOrUpdate(new ProjectEntry(updateProjectName, createProjectResponse.getId()));
        assertEquals(updateProjectResponse.getResult(),OperationResult.UPDATED);
        assertEquals(createProjectResponse.getId(), updateProjectResponse.getId());

        // --- get

        ProjectEntry projectEntry = projectResource.get(updateProjectName);
        assertEquals(projectEntry.getId(), createProjectResponse.getId());

        // --- create

        createProjectResponse = projectResource.createOrUpdate(new ProjectEntry(projectName, Collections.emptyMap()));
        assertTrue(createProjectResponse.isOk());

        // --- list

        List<ProjectEntry> projectList = projectResource.list(null, null, false);
        projectEntry = findProject(projectList, projectName);
        assertNotNull(projectEntry);

        // --- delete

        DeleteProjectResponse deleteProjectResponse = projectResource.delete(projectName);
        assertTrue(deleteProjectResponse.isOk());
    }

    @Test(timeout = 30000)
    public void testNonUniqueRepositoryNames() throws Exception {
        String projectName1 = "project1_" + randomString();
        String projectName2 = "project2_" + randomString();

        String repoName = "repo_" + randomString();

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new ProjectEntry(projectName1,
                Collections.singletonMap(repoName, new RepositoryEntry(null, null, repoName, "n/a", null, null, null, null, false, null))));

        projectResource.createOrUpdate(new ProjectEntry(projectName2,
                Collections.singletonMap(repoName, new RepositoryEntry(null, null, repoName, "n/a", null, null, null, null, false, null))));
    }

    @Test(timeout = 30000)
    public void testInventory() throws Exception {
        InventoryResource inventoryResource = proxy(InventoryResource.class);

        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        String inventoryName = "inventory_" + randomString();
        String updatedInventoryName = "updateInventory_" + randomString();

        // --- create

        CreateInventoryResponse cir = inventoryResource.createOrUpdate(orgName, new InventoryEntry(inventoryName));
        assertTrue(cir.isOk());
        assertNotNull(cir.getId());

        // --- update

        CreateInventoryResponse updateInventoryResponse = inventoryResource.createOrUpdate(orgName, new InventoryEntry(cir.getId(), updatedInventoryName, null, null, null, null, null));
        assertEquals(updateInventoryResponse.getResult(),OperationResult.UPDATED);
        assertEquals(updateInventoryResponse.getId(), cir.getId());

        // --- get

        InventoryEntry inventoryEntry = inventoryResource.get(orgName, updatedInventoryName);
        assertNotNull(inventoryEntry);

        // --- delete

        GenericOperationResultResponse deleteInventoryResponse = inventoryResource.delete(orgName, updatedInventoryName);
        assertTrue(deleteInventoryResponse.getResult() == OperationResult.DELETED);
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
    @SuppressWarnings("unchecked")
    public void testInventoryQuery() throws Exception {
        InventoryQueryResource resource = proxy(InventoryQueryResource.class);

        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        String inventoryName = "inventory_" + randomString();
        String queryName = "queryName_" + randomString();
        String text = "text_" + randomString();

        InventoryResource inventoryResource = proxy(InventoryResource.class);
        inventoryResource.createOrUpdate(orgName, new InventoryEntry(inventoryName));

        // ---

        InventoryDataResource inventoryDataResource = proxy(InventoryDataResource.class);
        inventoryDataResource.data(orgName, inventoryName, "/test", Collections.singletonMap("k", "v"));

        // --- create

        CreateInventoryQueryResponse cqr = resource.createOrUpdate(orgName, inventoryName, queryName, text);
        assertTrue(cqr.isOk());
        assertNotNull(cqr.getId());

        // --- update
        String updatedText = "select item_data::text from inventory_data";
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
                        new RepositoryEntry(null, null, repositoryName, "http://localhost", null, null, null, null, false, null))));

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

    @Test(timeout = 30000)
    public void testDashes() {
        String orgName = randomString() + "-test~";

        OrganizationResource organizationResource = proxy(OrganizationResource.class);
        organizationResource.createOrUpdate(new OrganizationEntry(orgName));

        organizationResource.get(orgName);

        // ---

        String teamName = randomString() + "-test~";

        TeamResource teamResource = proxy(TeamResource.class);
        teamResource.createOrUpdate(orgName, new TeamEntry(teamName));

        teamResource.get(orgName, teamName);

        // ---

        String projectName = randomString() + "-test~";

        com.walmartlabs.concord.server.api.org.project.ProjectResource projectResource = proxy(com.walmartlabs.concord.server.api.org.project.ProjectResource.class);
        projectResource.createOrUpdate(orgName, new ProjectEntry(projectName));

        projectResource.get(orgName, projectName);

        // ---

        String secretName = randomString() + "-test~";
        addPlainSecret(orgName, secretName, true, null, new byte[]{0, 1, 2, 3});

        SecretResource secretResource = proxy(SecretResource.class);
        secretResource.delete(orgName, secretName);
    }
    @Test(timeout = 30000)
    public void testTeam() throws Exception
    {
        String teamName = "team_" + randomString();
        String orgName = OrganizationManager.DEFAULT_ORG_NAME;
        TeamResource teamResource = proxy(TeamResource.class);

        // Create
        CreateTeamResponse teamResponse = teamResource.createOrUpdate(orgName,new TeamEntry(teamName));
        assertTrue(teamResponse.isOk());
        assertNotNull(teamResponse.getId());
        assertEquals(teamResponse.getResult(), OperationResult.CREATED);

        // Update Team by Name
        CreateTeamResponse updateTeamResponse = teamResource.createOrUpdate(orgName,new TeamEntry(null,null,null,teamName,"Update Description") );
        assertEquals(updateTeamResponse.getId(), teamResponse.getId());
        assertEquals(updateTeamResponse.getResult(),OperationResult.UPDATED);

        // Update Team by ID
        String updatedTeamName = "UpdatedName_"+ randomString();
        CreateTeamResponse updateTeamById = teamResource.createOrUpdate(orgName,new TeamEntry(teamResponse.getId(),null,null,updatedTeamName,"Name is updated"));
        assertEquals(teamResponse.getId(),updateTeamById.getId());
        assertEquals(updateTeamResponse.getResult(),OperationResult.UPDATED);

        // Get
        TeamEntry teamEntry = teamResource.get(orgName,updatedTeamName);
        assertEquals(teamResponse.getId(), teamEntry.getId());
        assertEquals(updatedTeamName,teamEntry.getName());

    }

    @Test(timeout = 30000)
    public void testSecrets() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationResource organizationResource = proxy(OrganizationResource.class);
        organizationResource.createOrUpdate(new OrganizationEntry(orgName));

        // ---

        String secretName = "secret_" + randomString();
        addPlainSecret(orgName, secretName, false, null, "hey".getBytes());

        // ---

        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();

        com.walmartlabs.concord.server.api.org.project.ProjectResource projectResource = proxy(com.walmartlabs.concord.server.api.org.project.ProjectResource.class);
        projectResource.createOrUpdate(orgName, new ProjectEntry(projectName,
                Collections.singletonMap(repoName,
                        new RepositoryEntry(null, null, repoName, "git@localhost:/test", null, null, null, secretName, false, null))));

        // ---

        SecretResource secretResource = proxy(SecretResource.class);
        secretResource.delete(orgName, secretName);

        /// ---

        ProjectEntry projectEntry = projectResource.get(orgName, projectName);
        Map<String, RepositoryEntry> repos = projectEntry.getRepositories();
        assertEquals(1, repos.size());

        RepositoryEntry repo = repos.get(repoName);
        assertNotNull(repo);
        assertNull(repo.getSecret());
    }

    private static ProjectEntry findProject(List<ProjectEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }

    @Test(timeout = 30000)
    public void testOrganization() throws Exception {
        OrganizationResource organizationResource = proxy(OrganizationResource.class);

        String organizationName = "organization_" + randomString();
        String updatedOrganizationName = "updateOrganization_" + randomString();

        // --- create

        CreateOrganizationResponse createOrganizationResponse = organizationResource.createOrUpdate(new OrganizationEntry(organizationName));
        assertTrue(createOrganizationResponse.isOk());
        assertNotNull(createOrganizationResponse.getId());

        // --- update

        CreateOrganizationResponse updateOrganizationResponse = organizationResource.createOrUpdate(new OrganizationEntry(createOrganizationResponse.getId(),updatedOrganizationName));
        assertEquals(updateOrganizationResponse.getResult(),OperationResult.UPDATED);
        assertEquals(updateOrganizationResponse.getId(), createOrganizationResponse.getId());

        // --- get

        OrganizationEntry organizationEntry = organizationResource.get(updatedOrganizationName);
        assertNotNull(organizationEntry);
        assertEquals(createOrganizationResponse.getId(), organizationEntry.getId());

        // --- list

        List<OrganizationEntry> organizationEntryList = organizationResource.list(true);
        assertNotNull(organizationEntryList);
        organizationEntry = findOrganization(organizationEntryList, updatedOrganizationName);
        assertNotNull(organizationEntry);
    }

    private static OrganizationEntry findOrganization(List<OrganizationEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }

}
