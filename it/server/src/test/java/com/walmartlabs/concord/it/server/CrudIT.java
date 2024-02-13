package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Execution(ExecutionMode.CONCURRENT)
public class CrudIT extends AbstractServerIT {

    @Test
    public void testOrgUpdate() throws Exception {
        String orgName = "org_" + randomString();
        Map<String, Object> meta = Collections.singletonMap("x", "123");

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry()
                .name(orgName)
                .meta(meta)
                .visibility(OrganizationEntry.VisibilityEnum.PUBLIC));

        // ---

        OrganizationEntry e = orgApi.getOrg(orgName);
        assertNotNull(e.getMeta());
        assertEquals("123", e.getMeta().get("x"));

        // ---

        orgApi.createOrUpdateOrg(e.meta(Collections.singletonMap("x", "234")));

        // ---

        e = orgApi.getOrg(orgName);
        assertNotNull(e.getMeta());
        assertEquals("234", e.getMeta().get("x"));
    }

    @Test
    public void testProject() throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String orgName = "Default";
        String projectName = "project_" + randomString();
        String updateProjectName = "updateProject_" + randomString();

        // --- create

        ProjectOperationResponse createResp = projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));
        assertTrue(createResp.getOk());
        assertNotNull(createResp.getId());

        // --- update

        ProjectOperationResponse updateResp = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .id(createResp.getId())
                .name(updateProjectName));
        assertEquals(ProjectOperationResponse.ResultEnum.UPDATED, updateResp.getResult());
        assertEquals(createResp.getId(), updateResp.getId());

        // --- get

        ProjectEntry projectEntry = projectsApi.getProject(orgName, updateProjectName);
        assertEquals(projectEntry.getId(), createResp.getId());

        // --- create

        createResp = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .repositories(Collections.emptyMap()));
        assertTrue(createResp.getOk());

        // --- list

        List<ProjectEntry> projectList = projectsApi.findProjects(orgName, null, null, null);
        projectEntry = findProject(projectList, projectName);
        assertNotNull(projectEntry);

        // --- update project's organization id

        String newOrgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        CreateOrganizationResponse createOrganizationResponse =
                orgApi.createOrUpdateOrg(new OrganizationEntry().name(newOrgName));
        assertTrue(createOrganizationResponse.getOk());
        assertNotNull(createOrganizationResponse.getId());

        ProjectOperationResponse moveResp = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .id(createResp.getId())
                .orgId(createOrganizationResponse.getId())
        );
        assertTrue(moveResp.getOk());
        assertEquals(ProjectOperationResponse.ResultEnum.UPDATED, moveResp.getResult());

        // --- error - empty name

        try {
            projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(""));
            fail("Project name should not be empty string");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("must match"), e::getMessage);
        }

        // --- error - null name and id

        try {
            projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                    .name(null)
                    .id(null)
            );
            fail("Project name should not be empty string");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("'name' is required"));
        }

        // --- update project's organization name

        moveResp = projectsApi.createOrUpdateProject(newOrgName, new ProjectEntry()
                .name(projectName)
                .orgName(orgName)
        );
        assertTrue(moveResp.getOk());
        assertEquals(ProjectOperationResponse.ResultEnum.UPDATED, moveResp.getResult());

        // --- delete

        GenericOperationResult deleteResp = projectsApi.deleteProject(orgName, projectName);
        assertTrue(deleteResp.getOk());
    }

    @Test
    public void testNonUniqueRepositoryNames() throws Exception {
        String orgName = "Default";

        String projectName1 = "project1_" + randomString();
        String projectName2 = "project2_" + randomString();

        String repoName = "repo_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName1)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .name(repoName)
                        .url(createRepo("repositoryRefresh"))
                        .branch("master"))));

        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName2)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .name(repoName)
                        .url(createRepo("repositoryRefresh"))
                        .branch("master"))));
    }

    @Test
    public void testInventory() throws Exception {
        InventoriesApi inventoriesApi = new InventoriesApi(getApiClient());

        String orgName = "Default";
        String inventoryName = "inventory_" + randomString();
        String updatedInventoryName = "updateInventory_" + randomString();

        // --- create

        CreateInventoryResponse cir = inventoriesApi.createOrUpdateInventory(orgName, new InventoryEntry().name(inventoryName));
        assertTrue(cir.getOk());
        assertNotNull(cir.getId());

        // --- update

        CreateInventoryResponse updateInventoryResponse = inventoriesApi.createOrUpdateInventory(orgName,
                new InventoryEntry()
                        .id(cir.getId())
                        .name(updatedInventoryName));
        assertEquals(updateInventoryResponse.getResult(), CreateInventoryResponse.ResultEnum.UPDATED);
        assertEquals(updateInventoryResponse.getId(), cir.getId());

        // --- get

        InventoryEntry inventoryEntry = inventoriesApi.getInventory(orgName, updatedInventoryName);
        assertNotNull(inventoryEntry);

        // -- list

        List<InventoryEntry> l = inventoriesApi.listInventories(orgName);
        assertTrue(l.stream().anyMatch(e -> updatedInventoryName.equals(e.getName()) && orgName.equals(e.getOrgName())));

        // --- delete

        GenericOperationResult deleteInventoryResponse = inventoriesApi.deleteInventory(orgName, updatedInventoryName);
        assertTrue(deleteInventoryResponse.getResult() == GenericOperationResult.ResultEnum.DELETED);
    }

    @Test
    public void testInventoryData() throws Exception {
        InventoryDataApi dataApi = new InventoryDataApi(getApiClient());

        String orgName = "Default";
        String inventoryName = "inventory_" + randomString();
        String itemPath = "/a";
        Map<String, Object> data = Collections.singletonMap("k", "v");

        InventoriesApi inventoriesApi = new InventoriesApi(getApiClient());
        inventoriesApi.createOrUpdateInventory(orgName, new InventoryEntry().name(inventoryName));

        // --- create

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) dataApi.updateInventoryData(orgName, inventoryName, itemPath, data);
        assertNotNull(result);
        assertEquals(Collections.singletonMap("a", data), result);

        // --- get

        @SuppressWarnings("unchecked")
        Map<String, Object> result2 = (Map<String, Object>) dataApi.getInventoryData(orgName, inventoryName, itemPath, false);
        assertNotNull(result2);
        assertEquals(Collections.singletonMap("a", data), result);

        // --- delete

        DeleteInventoryDataResponse didr = dataApi.deleteInventoryData(orgName, inventoryName, itemPath);
        assertNotNull(didr);
        assertTrue(didr.getOk());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInventoryQuery() throws Exception {
        InventoryQueriesApi queriesApi = new InventoryQueriesApi(getApiClient());

        String orgName = "Default";
        String inventoryName = "inventory_" + randomString();
        String queryName = "queryName_" + randomString();
        String text = "select * from test_" + randomString();

        InventoriesApi inventoriesApi = new InventoriesApi(getApiClient());
        inventoriesApi.createOrUpdateInventory(orgName, new InventoryEntry().name(inventoryName));

        // ---

        InventoryDataApi dataApi = new InventoryDataApi(getApiClient());
        dataApi.updateInventoryData(orgName, inventoryName, "/test", Collections.singletonMap("k", "v"));

        // --- create

        CreateInventoryQueryResponse cqr = queriesApi.createOrUpdateInventoryQuery(orgName, inventoryName, queryName, text);
        assertTrue(cqr.getOk());
        assertNotNull(cqr.getId());

        // --- update
        String updatedText = "select item_data::text from inventory_data";
        CreateInventoryQueryResponse uqr = queriesApi.createOrUpdateInventoryQuery(orgName, inventoryName, queryName, updatedText);
        assertTrue(uqr.getOk());
        assertNotNull(uqr.getId());

        // --- get
        InventoryQueryEntry e1 = queriesApi.getInventoryQuery(orgName, inventoryName, queryName);
        assertNotNull(e1);
        assertNotNull(e1.getId());
        assertEquals(queryName, e1.getName());
        assertEquals(updatedText, e1.getText());

        // --- list
        List<InventoryQueryEntry> list = queriesApi.listInventoryQueries(orgName, inventoryName);
        assertTrue(list.stream().anyMatch(e -> queryName.equals(e.getName()) && updatedText.equals(e.getText())));

        // --- exec
        @SuppressWarnings("unchecked")
        List<Object> result = queriesApi.executeInventoryQuery(orgName, inventoryName, queryName, null);
        assertNotNull(result);
        Map<String, Object> m = (Map<String, Object>) result.get(0);
        assertEquals(Collections.singletonMap("k", "v"), m);

        // --- delete
        DeleteInventoryQueryResponse dqr = queriesApi.deleteInventoryQuery(orgName, inventoryName, queryName);
        assertNotNull(dqr);
        assertTrue(dqr.getOk());
    }

    @Test
    public void testInvalidQueryName() throws Exception {
        InventoryQueriesApi queriesApi = new InventoryQueriesApi(getApiClient());

        String orgName = "Default";
        String inventoryName = "inventory_" + randomString();
        String queryName = "queryName_" + randomString();

        InventoriesApi inventoriesApi = new InventoriesApi(getApiClient());
        inventoriesApi.createOrUpdateInventory(orgName, new InventoryEntry().name(inventoryName));

        // ---

        try {
            queriesApi.executeInventoryQuery(orgName, inventoryName, queryName, null);
            fail("should fail");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("not found") && e.getMessage().contains(queryName));
        }
    }

    @Test
    public void testDashes() throws Exception {
        String orgName = randomString() + "-test~";

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        orgApi.getOrg(orgName);

        // ---

        String teamName = randomString() + "-test~";

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));

        teamsApi.getTeam(orgName, teamName);

        // ---

        String projectName = randomString() + "-test~";

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));

        projectsApi.getProject(orgName, projectName);

        // ---

        String secretName = randomString() + "-test~";
        addPlainSecret(orgName, secretName, true, null, new byte[]{0, 1, 2, 3});

        SecretsApi secretResource = new SecretsApi(getApiClient());
        secretResource.delete(orgName, secretName);
    }

    @Test
    public void testTeam() throws Exception {
        String teamName = "team_" + randomString();
        String orgName = "Default";
        TeamsApi teamsApi = new TeamsApi(getApiClient());

        // Create
        CreateTeamResponse teamResponse = teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));
        assertTrue(teamResponse.getOk());
        assertNotNull(teamResponse.getId());
        assertEquals(teamResponse.getResult(), CreateTeamResponse.ResultEnum.CREATED);

        // Update Team by Name
        CreateTeamResponse updateTeamResponse = teamsApi.createOrUpdateTeam(orgName, new TeamEntry()
                .name(teamName)
                .description("Update Description"));
        assertEquals(updateTeamResponse.getId(), teamResponse.getId());
        assertEquals(updateTeamResponse.getResult(), CreateTeamResponse.ResultEnum.UPDATED);

        // Update Team by ID
        String updatedTeamName = "UpdatedName_" + randomString();
        CreateTeamResponse updateTeamById = teamsApi.createOrUpdateTeam(orgName, new TeamEntry()
                .id(teamResponse.getId())
                .name(updatedTeamName)
                .description("Name is updated"));
        assertEquals(teamResponse.getId(), updateTeamById.getId());
        assertEquals(updateTeamResponse.getResult(), CreateTeamResponse.ResultEnum.UPDATED);

        // Get
        TeamEntry teamEntry = teamsApi.getTeam(orgName, updatedTeamName);
        assertEquals(teamResponse.getId(), teamEntry.getId());
        assertEquals(updatedTeamName, teamEntry.getName());
    }

    @Test
    public void testTeamManagementWithUserIds() throws Exception {
        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        organizationsApi.createOrUpdateOrg(new OrganizationEntry()
                .name(orgName));

        UsersApi usersApi = new UsersApi(getApiClient());

        String userAName = "userA_" + randomString();
        CreateUserResponse curA = usersApi.createOrUpdateUser(new CreateUserRequest()
                .type(CreateUserRequest.TypeEnum.LOCAL)
                .username(userAName));

        String userBName = "userB_" + randomString();
        CreateUserResponse curB = usersApi.createOrUpdateUser(new CreateUserRequest()
                .type(CreateUserRequest.TypeEnum.LOCAL)
                .username(userBName));

        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String teamName = "team_" + randomString();
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry()
                .name(teamName));

        // ---

        List<TeamUserEntry> l = teamsApi.listUserTeams(orgName, teamName);
        assertEquals(1, l.size());
        assertEquals("admin", l.get(0).getUsername());

        // ---

        teamsApi.addUsersToTeam(orgName, teamName, true, Arrays.asList(
                new TeamUserEntry().userId(curA.getId()),
                new TeamUserEntry().userId(curB.getId())));

        // ---

        l = teamsApi.listUserTeams(orgName, teamName);
        assertEquals(2, l.size());
        assertEquals(userAName.toLowerCase(), l.get(0).getUsername());
        assertEquals(userBName.toLowerCase(), l.get(1).getUsername());
    }

    @Test
    public void testSecrets() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        CreateOrganizationResponse orgResponse = orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        String secretName = "secret_" + randomString();
        addUsernamePassword(orgName, secretName, false, null, "username", "password");

        // ---

        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse projectResp = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .name(repoName)
                        .url(createRepo("repositoryRefresh"))
                        .branch("master")
                        .secretName(secretName))));
        UUID projectId = projectResp.getId();

        // ---

        SecretsApi secretsApi = new SecretsApi(getApiClient());
        SecretUpdateRequest updateReq;

        // --  Update secret project name

        updateReq = new SecretUpdateRequest();
        updateReq.projectName(projectName).orgId(orgResponse.getId());
        secretsApi.updateSecretV1(orgName, secretName, updateReq);
        updateReq.setProjectName("");
        secretsApi.updateSecretV1(orgName, secretName, updateReq);
        updateReq.setProjectName(null);
        secretsApi.updateSecretV1(orgName, secretName, updateReq);

        // --  Update secret project ID

        updateReq = new SecretUpdateRequest();
        updateReq.setProjectId(projectId);
        secretsApi.updateSecretV1(orgName, secretName, updateReq);
        updateReq.setProjectId(null);
        secretsApi.updateSecretV1(orgName, secretName, updateReq);

        // --  Delete secret

        secretsApi.delete(orgName, secretName);

        /// ---

        ProjectEntry projectEntry = projectsApi.getProject(orgName, projectName);
        assertNull(projectEntry.getRepositories());

        List<RepositoryEntry> repos = new RepositoriesApi(getApiClient()).listRepositories(orgName, projectName, null, null, null);
        assertEquals(1, repos.size());

        RepositoryEntry repo = repos.get(0);
        assertNotNull(repo);
        assertNull(repo.getSecretName());
    }

    private static ProjectEntry findProject(List<ProjectEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }

    @Test
    public void testOrganization() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        String updatedOrgName = "updateOrg_" + randomString();

        // --- create

        CreateOrganizationResponse createOrganizationResponse = orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        assertTrue(createOrganizationResponse.getOk());
        assertNotNull(createOrganizationResponse.getId());

        // --- update

        CreateOrganizationResponse updateOrganizationResponse = orgApi.createOrUpdateOrg(new OrganizationEntry()
                .id(createOrganizationResponse.getId())
                .name(updatedOrgName));
        assertEquals(updateOrganizationResponse.getResult(), CreateOrganizationResponse.ResultEnum.UPDATED);
        assertEquals(updateOrganizationResponse.getId(), createOrganizationResponse.getId());

        // --- get

        OrganizationEntry organizationEntry = orgApi.getOrg(updatedOrgName);
        assertNotNull(organizationEntry);
        assertEquals(createOrganizationResponse.getId(), organizationEntry.getId());

        // --- list

        List<OrganizationEntry> organizationEntryList = orgApi.listOrgs(true, null, null, null);
        assertNotNull(organizationEntryList);
        organizationEntry = findOrganization(organizationEntryList, updatedOrgName);
        assertNotNull(organizationEntry);
    }

    @Test
    public void testOrgVisibility() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();

        // create private org

        CreateOrganizationResponse createOrganizationResponse = orgApi.createOrUpdateOrg(new OrganizationEntry()
                .name(orgName)
                .visibility(OrganizationEntry.VisibilityEnum.PRIVATE));
        assertTrue(createOrganizationResponse.getOk());
        assertNotNull(createOrganizationResponse.getId());

        // --- private org available for admin

        List<OrganizationEntry> orgs = orgApi.listOrgs(false, null, null, null);
        assertTrue(orgs.stream().anyMatch(e -> e.getId().equals(createOrganizationResponse.getId())));

        // add the user A

        UsersApi usersApi = new UsersApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest().username(userAName).type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse apiKeyA = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userAName));

        setApiKey(apiKeyA.getKey());

        orgs = orgApi.listOrgs(true, null, null, null);
        assertTrue(orgs.stream().noneMatch(e -> e.getId().equals(createOrganizationResponse.getId())));
    }

    @Test
    public void testOrgMeta() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        Map<String, Object> meta = Collections.singletonMap("x", true);

        CreateOrganizationResponse cor = orgApi.createOrUpdateOrg(new OrganizationEntry()
                .name(orgName)
                .meta(meta));

        // ---

        OrganizationEntry e = orgApi.getOrg(orgName);
        assertNotNull(e);

        Map<String, Object> meta2 = e.getMeta();
        assertNotNull(meta2);
        assertEquals(meta.get("x"), meta2.get("x"));

        // ---

        meta = Collections.singletonMap("y", 123.0);
        orgApi.createOrUpdateOrg(new OrganizationEntry()
                .id(cor.getId())
                .meta(meta));

        e = orgApi.getOrg(orgName);
        assertNotNull(e);

        Map<String, Object> meta3 = e.getMeta();
        assertNotNull(meta3);
        assertEquals(1, meta3.size());
        assertEquals(meta.get("y"), meta3.get("y"));
    }

    @Test
    public void testPolicies() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String projectName = "project_" + randomString();
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));

        // ---

        String userName = "user_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        CreateUserResponse createUserResponse = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        // ---

        PolicyApi policyResource = new PolicyApi(getApiClient());

        String policyName = "policy_" + randomString();
        Map<String, Object> policyRules = Collections.singletonMap("x", 123);

        PolicyOperationResponse por = policyResource.createOrUpdatePolicy(new PolicyEntry()
                .name(policyName)
                .rules(policyRules));

        String newPolicyName = "policy2_" + randomString();
        policyResource.createOrUpdatePolicy(new PolicyEntry()
                .id(por.getId())
                .name(newPolicyName)
                .rules(policyRules));

        String policyForUser = "policy3_" + randomString();
        policyResource.createOrUpdatePolicy(new PolicyEntry()
                .name(policyForUser)
                .rules(policyRules));

        policyResource.createOrUpdatePolicy(new PolicyEntry()
                .id(por.getId())
                .name(policyName)
                .rules(policyRules));

        // ---

        PolicyEntry pe = policyResource.getPolicy(policyName);
        assertNotNull(pe);

        // ---

        policyResource.linkPolicy(policyName, new PolicyLinkEntry()
                .orgName(orgName)
                .projectName(projectName));

        List<PolicyEntry> l = policyResource.listPolicies(orgName, projectName, null, null, null);
        assertEquals(1, l.size());
        assertEquals(policyName, l.get(0).getName());

        // ---

        policyResource.linkPolicy(policyName, new PolicyLinkEntry().orgName(orgName));

        l = policyResource.listPolicies(orgName, null, null, null, null);
        assertEquals(1, l.size());
        assertEquals(policyName, l.get(0).getName());

        // ---

        policyResource.linkPolicy(policyName, new PolicyLinkEntry().userName(userName));

        l = policyResource.listPolicies(null, null, userName, null, null);
        assertEquals(1, l.size());
        assertEquals(policyName, l.get(0).getName());

        // ---

        policyResource.linkPolicy(policyForUser, new PolicyLinkEntry()
                .orgName(orgName)
                .projectName(projectName)
                .userName(userName));

        l = policyResource.listPolicies(orgName, projectName, userName, null, null);
        assertEquals(1, l.size());
        assertEquals(policyForUser, l.get(0).getName());

        // ---

        policyResource.unlinkPolicy(policyName, orgName, projectName, null, null, null);
        l = policyResource.listPolicies(orgName, projectName, null, null, null);
        assertEquals(1, l.size());
        l = policyResource.listPolicies(orgName, null, null, null, null);
        assertEquals(1, l.size());

        // ---

        policyResource.unlinkPolicy(policyName, orgName, null, null, null, null);
        l = policyResource.listPolicies(orgName, projectName, null, null, null);
        assertEquals(0, l.size());
        l = policyResource.listPolicies(orgName, null, null, null, null);
        assertEquals(0, l.size());

        // ---

        policyResource.unlinkPolicy(policyName, null, null, userName, null, null);
        l = policyResource.listPolicies(null, null, userName, null, null);
        assertEquals(0, l.size());
        l = policyResource.listPolicies(orgName, null, null, null, null);
        assertEquals(0, l.size());

        // ---

        policyResource.unlinkPolicy(policyForUser, orgName, projectName, userName, null, null);
        l = policyResource.listPolicies(orgName, projectName, userName, null, null);
        assertEquals(0, l.size());
        l = policyResource.listPolicies(orgName, null, null, null, null);
        assertEquals(0, l.size());

        // ---

        policyResource.deletePolicy(policyName);
        l = policyResource.listPolicies(null, null, null, null, null);
        for (PolicyEntry e : l) {
            if (policyName.equals(e.getName())) {
                fail("Should've been removed: " + e.getName());
            }
        }

        usersApi.deleteUser(createUserResponse.getId());

        policyResource.deletePolicy(policyForUser);
    }

    @Test
    public void testRoles() throws Exception {
        RolesApi rolesApi = new RolesApi(getApiClient());

        String roleName = "role_" + randomString();

        // --- create

        RoleOperationResponse createRoleResponse = rolesApi.createOrUpdateRole(new RoleEntry().name(roleName));
        assertEquals(RoleOperationResponse.ResultEnum.CREATED, createRoleResponse.getResult());
        assertNotNull(createRoleResponse.getId());

        // --- update

        RoleOperationResponse updateRoleResponse = rolesApi.createOrUpdateRole(new RoleEntry()
                .id(createRoleResponse.getId())
                .name(roleName)
                .permissions(Collections.singleton("getProcessQueueAllOrgs")));
        assertEquals(RoleOperationResponse.ResultEnum.UPDATED, updateRoleResponse.getResult());

        // --- get

        RoleEntry roleEntry = rolesApi.getRole(roleName);
        assertNotNull(roleEntry);
        assertEquals(createRoleResponse.getId(), roleEntry.getId());
        assertEquals("getProcessQueueAllOrgs", roleEntry.getPermissions().iterator().next());

        // --- list

        List<RoleEntry> roleEntryList = rolesApi.listRoles();
        assertNotNull(roleEntryList);

        GenericOperationResult r = rolesApi.deleteRole(roleName);
        assertEquals(GenericOperationResult.ResultEnum.DELETED, r.getResult());
    }

    @Test
    public void testChangeOrganization() throws Exception {

        String orgName = "Default";
        String secondOrgName = "SecondOrg_" + randomString();

        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());

        UUID defaultOrgId = organizationsApi.getOrg(orgName).getId();

        // create a second organization
        CreateOrganizationResponse createOrgResponse = organizationsApi.createOrUpdateOrg(new OrganizationEntry()
                .name(secondOrgName)
                .visibility(OrganizationEntry.VisibilityEnum.PUBLIC));
        assertTrue(createOrgResponse.getOk());
        assertNotNull(createOrgResponse.getId());

        // -- test change org for project
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        String projectName = "project_" + randomString();

        // create a project in Default org
        ProjectOperationResponse createResp = projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));
        assertTrue(createResp.getOk());
        assertNotNull(createResp.getId());

        // -- move project to second organization by org Name
        ProjectOperationResponse moveResponse = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .orgName(secondOrgName));
        assertTrue(moveResponse.getOk());
        assertEquals("UPDATED", moveResponse.getResult().getValue());
        assertNotNull(projectsApi.getProject(secondOrgName, projectName));

        // -- move project back to Default organization by org Id
        moveResponse = projectsApi.createOrUpdateProject(secondOrgName, new ProjectEntry()
                .name(projectName)
                .orgId(defaultOrgId));
        assertTrue(moveResponse.getOk());
        assertEquals("UPDATED", moveResponse.getResult().getValue());
        assertNotNull(projectsApi.getProject(orgName, projectName));

        // -- test change org for secret
        SecretsApi secretsApi = new SecretsApi(getApiClient());
        String secretName = "secret_" + randomString();

        // create a secret in Default org
        addPlainSecret(orgName, secretName, false, null, "hey".getBytes());

        // -- move secret to second organization by org Name
        GenericOperationResult moveSecretResponse = secretsApi.updateSecretV1(orgName, secretName,
                new SecretUpdateRequest().orgName(secondOrgName));
        assertTrue(moveSecretResponse.getOk());
        assertEquals("UPDATED", moveSecretResponse.getResult().getValue());
        assertNotNull(new SecretsV2Api(getApiClient()).getSecret(secondOrgName, secretName));

        // -- move secret back to Default organization by org Id
        moveSecretResponse = secretsApi.updateSecretV1(secondOrgName, secretName, new SecretUpdateRequest().orgId(defaultOrgId));
        assertTrue(moveSecretResponse.getOk());
        assertEquals("UPDATED", moveSecretResponse.getResult().getValue());
        assertNotNull(new SecretsV2Api(getApiClient()).getSecret(orgName, secretName));
    }

    private static OrganizationEntry findOrganization(List<OrganizationEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }
}
