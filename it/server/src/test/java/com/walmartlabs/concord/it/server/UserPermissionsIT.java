package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static org.junit.Assert.*;

public class UserPermissionsIT extends AbstractServerIT {

    private static final Logger log = LoggerFactory.getLogger(UserPermissionsIT.class);

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testUserPermissions() throws Exception {
        // Create userA - concordAdmin role
        UsersApi usersApi = new UsersApi(getApiClient());
        String userAName = "userA_" + randomString();
        CreateUserResponse createUserResponseA = usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userAName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));
        assertTrue(createUserResponseA.isOk());
        usersApi.updateUserRoles(userAName, new UpdateUserRolesRequest()
                .setRoles(Collections.singletonList("concordAdmin")));
        log.info("User A: name " + createUserResponseA.getUsername() +
                " , id:" + createUserResponseA.getId());

        // Create userB
        String userBName = "userB_" + randomString();
        CreateUserResponse createUserResponseB = usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userBName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));
        assertTrue(createUserResponseB.isOk());
        log.info("User B: name " + createUserResponseB.getUsername() +
                " , id:" + createUserResponseB.getId());

        // Create orgA - Private
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        String orgAName = "orgA_" + randomString();
        Map<String, Object> meta = Collections.singletonMap(orgAName, "123");
        CreateOrganizationResponse createOrganizationResponseA = orgApi.createOrUpdate(new OrganizationEntry()
                .setName(orgAName)
                .setMeta(meta)
                .setVisibility(OrganizationEntry.VisibilityEnum.PRIVATE));
        assertTrue(createOrganizationResponseA.isOk());
        log.info("Org A: name - {}, id: {}", orgAName , createOrganizationResponseA.getId());

        // Create orgB - Public
        String orgBName = "orgB_" + randomString();
        meta = Collections.singletonMap(orgBName, "123");
        CreateOrganizationResponse createOrganizationResponseB = orgApi.createOrUpdate(new OrganizationEntry()
                .setName(orgBName)
                .setMeta(meta)
                .setVisibility(OrganizationEntry.VisibilityEnum.PUBLIC));
        assertTrue(createOrganizationResponseB.isOk());
        log.info("Org B: name - {}, id: {}", orgBName , createOrganizationResponseB.getId());

        // Create projectA under orgA - private project
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        String projectAName = "myProjectA_" + randomString();
        ProjectOperationResponse cprA = projectsApi.createOrUpdate(orgAName, new ProjectEntry()
                .setName(projectAName)
                .setAcceptsRawPayload(true)
                .setVisibility(ProjectEntry.VisibilityEnum.PRIVATE)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));
        assertTrue(cprA.isOk());
        log.info("Project A - name : {} , id: {}", projectAName, cprA.getId());

        // Create projectB under orgB
        String projectBName = "myProjectB_" + randomString();
        ProjectOperationResponse cprB = projectsApi.createOrUpdate(orgBName, new ProjectEntry()
                .setName(projectBName)
                .setAcceptsRawPayload(true)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));
        assertTrue(cprB.isOk());
        log.info("Project B - name : {} , id: {}", projectBName, cprB.getId());

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakrA = apiKeyResource.create(new CreateApiKeyRequest()
                .setUserId(createUserResponseA.getId())
                .setUserType(CreateApiKeyRequest.UserTypeEnum.LOCAL));

        assertTrue(cakrA.isOk());
        log.info("Api key user A: {}", cakrA.getKey());

        CreateApiKeyResponse cakrB = apiKeyResource.create(new CreateApiKeyRequest()
                .setUserId(createUserResponseB.getId())
                .setUserType(CreateApiKeyRequest.UserTypeEnum.LOCAL));
        assertTrue(cakrB.isOk());
        log.info("Api key user B: {}", cakrB.getKey());

        setApiKey(cakrA.getKey());
        ProcessV2Api processV2Api = new ProcessV2Api(getApiClient());

        // Run a process under projectA, orgA
        ProcessApi processApi = new ProcessApi(getApiClient());
        byte[] payload = archive(UserPermissionsIT.class.getResource("example").toURI());

        StartProcessResponse spr = start(orgAName, projectAName, null, null, payload);
        log.info("Instance id A: {}", spr.getInstanceId());
        wait(processApi, spr);

        // Run a process under project B, org B
        spr = start(orgBName, projectBName, null, null, payload);
        log.info("Instance id B: {} ", spr.getInstanceId());
        wait(processApi, spr);

        //list(String org, String project, UUID projectId, String afterCreatedAt, String beforeCreatedAt, List<String> tags, String status, String initiator, UUID parentInstanceId, Integer limit, Integer offset)
        setApiKey(cakrA.getKey());
        List<ProcessEntry> processEntries =
                processV2Api.list(null, null, null, null, null, null,
                        null, null, null, null, null, null,
                        null, null, null);
        log.info("Process size userA all processes: {}" , processEntries.size());
        assertEquals(2, processEntries.size());

        processEntries =
                processV2Api.list(null, orgAName, null, null, null, null,
                        null, null, null, null, null, null,
                        null, null, null);
        log.info("Process size userA all processes: {}" , processEntries.size());
        assertEquals(1, processEntries.size());

        // Query using user B's key
        setApiKey(cakrB.getKey());
        processEntries =
                processV2Api.list(null, null, null, null, null, null,
                        null, null, null, null, null, null,
                        null, null, null);
        assertEquals(0, processEntries.size());
        log.info("Process size userB all: {}" , processEntries.size());

        processEntries =
                processV2Api.list(null, orgAName, null, null, null, null,
                        null, null, null, null, null, null,
                        null, null, null);
        log.info("Process size userB orgA: {}" ,  processEntries.size());
        // Should this fail ?
        assertEquals(0, processEntries.size());
    }

    private void wait(ProcessApi processApi, StartProcessResponse spr) {
        try {
            processApi.waitForCompletion(spr.getInstanceId(), 3000L);
        } catch (ApiException e) {
            String s = e.getResponseBody();
            ProcessEntry pir = getApiClient().getJSON().deserialize(s, ProcessEntry.class);
            assertTrue("Unexpected status: " + pir.getStatus(), ProcessEntry.StatusEnum.RUNNING.equals(pir.getStatus())
                    || ProcessEntry.StatusEnum.ENQUEUED.equals(pir.getStatus())
                    || ProcessEntry.StatusEnum.PREPARING.equals(pir.getStatus())
                    || ProcessEntry.StatusEnum.STARTING.equals(pir.getStatus()));
        }
    }

}
