package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static org.junit.Assert.assertTrue;

public class UserPermissionsIT extends AbstractServerIT {

    private static final Logger log = LoggerFactory.getLogger(UserPermissionsIT.class);

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testUserPermissions() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());
        String userAName = "userA_" + randomString();
        CreateUserResponse createUserResponseA = usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userAName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));
        Assert.assertTrue(createUserResponseA.isOk());
        usersApi.updateUserRoles(userAName, new UpdateUserRolesRequest()
                .setRoles(Collections.singletonList("concordAdmin")));
        log.info("User A: name " + createUserResponseA.getUsername() +
                " , id:" + createUserResponseA.getId());

        String userBName = "userB_" + randomString();
        CreateUserResponse createUserResponseB = usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userBName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));
        Assert.assertTrue(createUserResponseB.isOk());
        log.info("User B: name " + createUserResponseB.getUsername() +
                " , id:" + createUserResponseB.getId());

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        String orgAName = "orgA_" + randomString();
        Map<String, Object> meta = Collections.singletonMap(orgAName, "123");
        CreateOrganizationResponse createOrganizationResponseA = orgApi.createOrUpdate(new OrganizationEntry()
                .setName(orgAName)
                .setMeta(meta)
                .setVisibility(OrganizationEntry.VisibilityEnum.PRIVATE));
        Assert.assertTrue(createOrganizationResponseA.isOk());
        log.info("Org A: name - {}, id: {}", orgAName , createOrganizationResponseA.getId());

        String orgBName = "orgB_" + randomString();
        meta = Collections.singletonMap(orgBName, "123");
        CreateOrganizationResponse createOrganizationResponseB = orgApi.createOrUpdate(new OrganizationEntry()
                .setName(orgBName)
                .setMeta(meta)
                .setVisibility(OrganizationEntry.VisibilityEnum.PUBLIC));
        Assert.assertTrue(createOrganizationResponseB.isOk());
        log.info("Org B: name - {}, id: {}", orgBName , createOrganizationResponseB.getId());

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        String projectAName = "myProjectA_" + randomString();
        ProjectOperationResponse cprA = projectsApi.createOrUpdate(orgAName, new ProjectEntry()
                .setName(projectAName)
                .setAcceptsRawPayload(true)
                .setVisibility(ProjectEntry.VisibilityEnum.PRIVATE)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));
        Assert.assertTrue(cprA.isOk());
        log.info("Project A - name : {} , id: {}", projectAName, cprA.getId());

        String projectBName = "myProjectB_" + randomString();
        ProjectOperationResponse cprB = projectsApi.createOrUpdate(orgBName, new ProjectEntry()
                .setName(projectBName)
                .setAcceptsRawPayload(true)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));
        Assert.assertTrue(cprB.isOk());
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

        // Run a process under org1, project1
        ProcessApi processApi = new ProcessApi(getApiClient());
        byte[] payload = archive(UserPermissionsIT.class.getResource("example").toURI());

        StartProcessResponse spr = start(orgAName, projectAName, null, null, payload);
        log.info("Instance id A: {}", spr.getInstanceId());

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

        // Run a process under org B, project B
        spr = start(orgBName, projectBName, null, null, payload);
        log.info("Instance id B: {} ", spr.getInstanceId());
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

        //list(String org, String project, UUID projectId, String afterCreatedAt, String beforeCreatedAt, List<String> tags, String status, String initiator, UUID parentInstanceId, Integer limit, Integer offset)
        setApiKey(cakrA.getKey());
        List<ProcessEntry> processEntries =
                processV2Api.list(null, null, null, null, null, null,
                        null, null, null, null, null, null,
                        null, null, null);
        log.info("Process size userA all processes: {}" , processEntries.size());
        Assert.assertEquals(processEntries.size(), 2, "User A should be shown 2 processes");

        processEntries =
                processV2Api.list(null, orgAName, null, null, null, null,
                        null, null, null, null, null, null,
                        null, null, null);
        log.info("Process size userA all processes: {}" , processEntries.size());
        Assert.assertEquals(processEntries.size(), 1, "User A should be shown 1 processes when filtered by org");

        // Query using user B's key
        setApiKey(cakrB.getKey());
        processEntries =
                processV2Api.list(null, null, null, null, null, null,
                        null, null, null, null, null, null,
                        null, null, null);
        Assert.assertEquals(processEntries.size(), 0, "User B should be shown 0 processes");
        log.info("Process size userB all: {}" , processEntries.size());

        processEntries =
                processV2Api.list(null, orgAName, null, null, null, null,
                        null, null, null, null, null, null,
                        null, null, null);
        log.info("Process size userB orgA: {}" ,  processEntries.size());
        Assert.assertEquals(processEntries.size(), 0, "User B should be shown 0 processes when filtered by org");

    }

}
