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
import com.walmartlabs.concord.client.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(ParallelRunner.class)
public class CrudIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void testProject() throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String orgName = "Default";
        String projectName = "project_" + randomString();
        String updateProjectName = "updateProject_" + randomString();

        // --- create

        ProjectOperationResponse createResp = projectsApi.createOrUpdate(orgName, new ProjectEntry().setName(projectName));
        assertTrue(createResp.isOk());
        assertNotNull(createResp.getId());

        // --- update

        ProjectOperationResponse updateResp = projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setId(createResp.getId())
                .setName(updateProjectName));
        assertEquals(updateResp.getResult(), ProjectOperationResponse.ResultEnum.UPDATED);
        assertEquals(createResp.getId(), updateResp.getId());

        // --- get

        ProjectEntry projectEntry = projectsApi.get(orgName, updateProjectName);
        assertEquals(projectEntry.getId(), createResp.getId());

        // --- create

        createResp = projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(Collections.emptyMap()));
        assertTrue(createResp.isOk());

        // --- list

        List<ProjectEntry> projectList = projectsApi.list(orgName);
        projectEntry = findProject(projectList, projectName);
        assertNotNull(projectEntry);

        // --- delete

        GenericOperationResult deleteResp = projectsApi.delete(orgName, projectName);
        assertTrue(deleteResp.isOk());
    }

    @Test(timeout = 60000)
    public void testNonUniqueRepositoryNames() throws Exception {
        String orgName = "Default";

        String projectName1 = "project1_" + randomString();
        String projectName2 = "project2_" + randomString();

        String repoName = "repo_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName1)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setName(repoName)
                        .setUrl("n/a"))));

        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName2)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setName(repoName)
                        .setUrl("n/a"))));
    }

    @Test(timeout = 60000)
    public void testInventory() throws Exception {
        InventoriesApi inventoriesApi = new InventoriesApi(getApiClient());

        String orgName = "Default";
        String inventoryName = "inventory_" + randomString();
        String updatedInventoryName = "updateInventory_" + randomString();

        // --- create

        CreateInventoryResponse cir = inventoriesApi.createOrUpdate(orgName, new InventoryEntry().setName(inventoryName));
        assertTrue(cir.isOk());
        assertNotNull(cir.getId());

        // --- update

        CreateInventoryResponse updateInventoryResponse = inventoriesApi.createOrUpdate(orgName,
                new InventoryEntry()
                        .setId(cir.getId())
                        .setName(updatedInventoryName));
        assertEquals(updateInventoryResponse.getResult(), CreateInventoryResponse.ResultEnum.UPDATED);
        assertEquals(updateInventoryResponse.getId(), cir.getId());

        // --- get

        InventoryEntry inventoryEntry = inventoriesApi.get(orgName, updatedInventoryName);
        assertNotNull(inventoryEntry);

        // --- delete

        GenericOperationResult deleteInventoryResponse = inventoriesApi.delete(orgName, updatedInventoryName);
        assertTrue(deleteInventoryResponse.getResult() == GenericOperationResult.ResultEnum.DELETED);
    }

    @Test(timeout = 60000)
    public void testInventoryData() throws Exception {
        InventoryDataApi dataApi = new InventoryDataApi(getApiClient());

        String orgName = "Default";
        String inventoryName = "inventory_" + randomString();
        String itemPath = "/a";
        Map<String, Object> data = Collections.singletonMap("k", "v");

        InventoriesApi inventoriesApi = new InventoriesApi(getApiClient());
        inventoriesApi.createOrUpdate(orgName, new InventoryEntry().setName(inventoryName));

        // --- create

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) dataApi.data(orgName, inventoryName, itemPath, data);
        assertNotNull(result);
        assertEquals(Collections.singletonMap("a", data), result);

        // --- get

        @SuppressWarnings("unchecked")
        Map<String, Object> result2 = (Map<String, Object>) dataApi.get(orgName, inventoryName, itemPath);
        assertNotNull(result2);
        assertEquals(Collections.singletonMap("a", data), result);

        // --- delete

        DeleteInventoryDataResponse didr = dataApi.delete(orgName, inventoryName, itemPath);
        assertNotNull(didr);
        assertTrue(didr.isOk());
    }

    @Test(timeout = 60000)
    @SuppressWarnings("unchecked")
    public void testInventoryQuery() throws Exception {
        InventoryQueriesApi resource = new InventoryQueriesApi(getApiClient());

        String orgName = "Default";
        String inventoryName = "inventory_" + randomString();
        String queryName = "queryName_" + randomString();
        String text = "text_" + randomString();

        InventoriesApi inventoriesApi = new InventoriesApi(getApiClient());
        inventoriesApi.createOrUpdate(orgName, new InventoryEntry().setName(inventoryName));

        // ---

        InventoryDataApi dataApi = new InventoryDataApi(getApiClient());
        dataApi.data(orgName, inventoryName, "/test", Collections.singletonMap("k", "v"));

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

    @Test(timeout = 60000)
    public void testLanding() throws Exception {
        LandingPagesApi resource = new LandingPagesApi(getApiClient());

        String orgName = "Default";
        String projectName = "project_" + randomString();
        String repositoryName = "repository_" + randomString();
        String name = "lp-name-1";
        String description = "description";
        String icon = Base64.encode("icon".getBytes());

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(Collections.singletonMap(repositoryName, new RepositoryEntry()
                        .setName(repositoryName)
                        .setUrl("http://localhost"))));

        // --- create
        LandingEntry entry = new LandingEntry()
                .setProjectName(projectName)
                .setRepositoryName(repositoryName)
                .setName(name)
                .setDescription(description)
                .setIcon(icon);
        CreateLandingResponse result = resource.createOrUpdate(orgName, entry);
        assertNotNull(result);
        assertTrue(result.isOk());
        assertNotNull(result.getId());
        assertEquals(CreateLandingResponse.ResultEnum.CREATED, result.getResult());

        // --- update
        result = resource.createOrUpdate(orgName, new LandingEntry()
                .setId(result.getId())
                .setProjectName(projectName)
                .setRepositoryName(repositoryName)
                .setName(name)
                .setDescription(description)
                .setIcon(icon));
        assertNotNull(result);
        assertTrue(result.isOk());
        assertNotNull(result.getId());
        assertEquals(CreateLandingResponse.ResultEnum.UPDATED, result.getResult());

        // --- list
        List<LandingEntry> listResult = resource.list(orgName);
        assertNotNull(listResult);
    }

    @Test(timeout = 60000)
    public void testDashes() throws Exception {
        String orgName = randomString() + "-test~";

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        orgApi.get(orgName);

        // ---

        String teamName = randomString() + "-test~";

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));

        teamsApi.get(orgName, teamName);

        // ---

        String projectName = randomString() + "-test~";

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry().setName(projectName));

        projectsApi.get(orgName, projectName);

        // ---

        String secretName = randomString() + "-test~";
        addPlainSecret(orgName, secretName, true, null, new byte[]{0, 1, 2, 3});

        SecretsApi secretResource = new SecretsApi(getApiClient());
        secretResource.delete(orgName, secretName);
    }

    @Test(timeout = 60000)
    public void testTeam() throws Exception {
        String teamName = "team_" + randomString();
        String orgName = "Default";
        TeamsApi teamsApi = new TeamsApi(getApiClient());

        // Create
        CreateTeamResponse teamResponse = teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));
        assertTrue(teamResponse.isOk());
        assertNotNull(teamResponse.getId());
        assertEquals(teamResponse.getResult(), CreateTeamResponse.ResultEnum.CREATED);

        // Update Team by Name
        CreateTeamResponse updateTeamResponse = teamsApi.createOrUpdate(orgName, new TeamEntry()
                .setName(teamName)
                .setDescription("Update Description"));
        assertEquals(updateTeamResponse.getId(), teamResponse.getId());
        assertEquals(updateTeamResponse.getResult(), CreateTeamResponse.ResultEnum.UPDATED);

        // Update Team by ID
        String updatedTeamName = "UpdatedName_" + randomString();
        CreateTeamResponse updateTeamById = teamsApi.createOrUpdate(orgName, new TeamEntry()
                .setId(teamResponse.getId())
                .setName(updatedTeamName)
                .setDescription("Name is updated"));
        assertEquals(teamResponse.getId(), updateTeamById.getId());
        assertEquals(updateTeamResponse.getResult(), CreateTeamResponse.ResultEnum.UPDATED);

        // Get
        TeamEntry teamEntry = teamsApi.get(orgName, updatedTeamName);
        assertEquals(teamResponse.getId(), teamEntry.getId());
        assertEquals(updatedTeamName, teamEntry.getName());

    }

    @Test(timeout = 60000)
    public void testSecrets() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String secretName = "secret_" + randomString();
        addPlainSecret(orgName, secretName, false, null, "hey".getBytes());

        // ---

        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setName(repoName)
                        .setUrl("git@localhost:/test")
                        .setSecretName(secretName))));

        // ---

        SecretsApi secretsApi = new SecretsApi(getApiClient());
        secretsApi.delete(orgName, secretName);

        /// ---

        ProjectEntry projectEntry = projectsApi.get(orgName, projectName);
        Map<String, RepositoryEntry> repos = projectEntry.getRepositories();
        assertEquals(1, repos.size());

        RepositoryEntry repo = repos.get(repoName);
        assertNotNull(repo);
        assertNull(repo.getSecretName());
    }

    private static ProjectEntry findProject(List<ProjectEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }

    @Test(timeout = 60000)
    public void testOrganization() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        String updatedOrgName = "updateOrg_" + randomString();

        // --- create

        CreateOrganizationResponse createOrganizationResponse = orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));
        assertTrue(createOrganizationResponse.isOk());
        assertNotNull(createOrganizationResponse.getId());

        // --- update

        CreateOrganizationResponse updateOrganizationResponse = orgApi.createOrUpdate(new OrganizationEntry()
                .setId(createOrganizationResponse.getId())
                .setName(updatedOrgName));
        assertEquals(updateOrganizationResponse.getResult(), CreateOrganizationResponse.ResultEnum.UPDATED);
        assertEquals(updateOrganizationResponse.getId(), createOrganizationResponse.getId());

        // --- get

        OrganizationEntry organizationEntry = orgApi.get(updatedOrgName);
        assertNotNull(organizationEntry);
        assertEquals(createOrganizationResponse.getId(), organizationEntry.getId());

        // --- list

        List<OrganizationEntry> organizationEntryList = orgApi.list(true);
        assertNotNull(organizationEntryList);
        organizationEntry = findOrganization(organizationEntryList, updatedOrgName);
        assertNotNull(organizationEntry);
    }

    @Test(timeout = 60000)
    public void testOrgVisibility() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();

        // create private org

        CreateOrganizationResponse createOrganizationResponse = orgApi.createOrUpdate(new OrganizationEntry()
                .setName(orgName)
                .setVisibility(OrganizationEntry.VisibilityEnum.PRIVATE));
        assertTrue(createOrganizationResponse.isOk());
        assertNotNull(createOrganizationResponse.getId());

        // --- private org available for admin

        List<OrganizationEntry> orgs = orgApi.list(false);
        assertTrue(orgs.stream().anyMatch(e -> e.getId().equals(createOrganizationResponse.getId())));

        // add the user A

        UsersApi usersApi = new UsersApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest().setUsername(userAName).setType(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userAName));

        setApiKey(apiKeyA.getKey());

        orgs = orgApi.list(true);
        assertTrue(orgs.stream().noneMatch(e -> e.getId().equals(createOrganizationResponse.getId())));
    }

    @Test(timeout = 60000)
    public void testOrgMeta() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        Map<String, Object> meta = Collections.singletonMap("x", true);

        CreateOrganizationResponse cor = orgApi.createOrUpdate(new OrganizationEntry()
                .setName(orgName)
                .setMeta(meta));

        // ---

        OrganizationEntry e = orgApi.get(orgName);
        assertNotNull(e);

        Map<String, Object> meta2 = e.getMeta();
        assertNotNull(meta2);
        assertEquals(meta.get("x"), meta2.get("x"));

        // ---

        meta = Collections.singletonMap("y", 123.0);
        orgApi.createOrUpdate(new OrganizationEntry()
                .setId(cor.getId())
                .setMeta(meta));

        e = orgApi.get(orgName);
        assertNotNull(e);

        Map<String, Object> meta3 = e.getMeta();
        assertNotNull(meta3);
        assertEquals(1, meta3.size());
        assertEquals(meta.get("y"), meta3.get("y"));
    }

    @Test(timeout = 60000)
    public void testPolicies() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String projectName = "project_" + randomString();
        projectsApi.createOrUpdate(orgName, new ProjectEntry().setName(projectName));

        // ---

        PolicyApi policyResource = new PolicyApi(getApiClient());

        String policyName = "policy_" + randomString();
        Map<String, Object> policyRules = Collections.singletonMap("x", 123);

        PolicyOperationResponse por = policyResource.createOrUpdate(new PolicyEntry()
                .setName(policyName)
                .setRules(policyRules));

        String newPolicyName = "policy2_" + randomString();
        policyResource.createOrUpdate(new PolicyEntry()
                .setId(por.getId())
                .setName(newPolicyName)
                .setRules(policyRules));

        policyResource.createOrUpdate(new PolicyEntry()
                .setId(por.getId())
                .setName(policyName)
                .setRules(policyRules));

        // ---

        PolicyEntry pe = policyResource.get(policyName);
        assertNotNull(pe);

        // ---

        policyResource.link(policyName, new PolicyLinkEntry()
                .setOrgName(orgName)
                .setProjectName(projectName));

        List<PolicyEntry> l = policyResource.list(orgName, projectName);
        assertEquals(1, l.size());
        assertEquals(policyName, l.get(0).getName());

        // ---

        policyResource.link(policyName, new PolicyLinkEntry().setOrgName(orgName));

        l = policyResource.list(orgName, null);
        assertEquals(1, l.size());
        assertEquals(policyName, l.get(0).getName());

        // ---

        policyResource.unlink(policyName, orgName, projectName);
        l = policyResource.list(orgName, projectName);
        assertEquals(1, l.size());
        l = policyResource.list(orgName, null);
        assertEquals(1, l.size());

        // ---

        policyResource.unlink(policyName, orgName, null);
        l = policyResource.list(orgName, projectName);
        assertEquals(0, l.size());
        l = policyResource.list(orgName, null);
        assertEquals(0, l.size());

        // ---

        policyResource.delete(policyName);
        l = policyResource.list(null, null);
        for (PolicyEntry e : l) {
            if (policyName.equals(e.getName())) {
                fail("Should've been removed: " + e.getName());
            }
        }
    }

    private static OrganizationEntry findOrganization(List<OrganizationEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }
}
