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
import com.walmartlabs.concord.client2.ProcessEntry.StatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FailureHandlingIT extends AbstractServerIT {

    @Test
    public void testFailure() throws Exception {
        ProcessApi processApi = new ProcessApi(getApiClient());

        // prepare the payload

        byte[] payload = archive(ProcessIT.class.getResource("failureHandling").toURI());

        // start the process and wait for it to fail

        StartProcessResponse spr = start(payload);

        waitForStatus(getApiClient(), spr.getInstanceId(), StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.listProcessForms(spr.getInstanceId());
        assertEquals(1, forms.size());

        Map<String, Object> data = Collections.singletonMap("firstName", "first-name");
        FormSubmitResponse fsr = formsApi.submitFormAsMultipart(spr.getInstanceId(), forms.get(0).getName(), data);
        assertTrue(fsr.getOk());

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), StatusEnum.FAILED);

        // check the logs for the error message

        byte[] ab = getLog(pir.getInstanceId());
        assertLogAtLeast(".*boom!.*", 1, ab);

        // find the child processes

        ProcessEntry child = waitForChild(processApi, spr.getInstanceId(), ProcessEntry.KindEnum.FAILURE_HANDLER, StatusEnum.FINISHED);

        // check the logs for the successful message

        ab = getLog(child.getInstanceId());
        assertLog(".*lastError:.*boom.*", ab);
        assertLog(".*projectInfo: \\{.*orgName=Default.*\\}.*", ab);
        assertLog(".*processInfo: \\{.*sessionKey=.*\\}.*", ab);
        assertLog(".*initiator: \\{.*username=.*\\}.*", ab);
    }

    @Test
    public void testFailureHandlingError() throws Exception {
        ProcessApi processApi = new ProcessApi(getApiClient());
        byte[] payload = archive(ProcessIT.class.getResource("failureHandlingError").toURI());

        StartProcessResponse spr = start(payload);
        waitForStatus(getApiClient(), spr.getInstanceId(), StatusEnum.FAILED);

        // find the child processes

        waitForChild(processApi, spr.getInstanceId(), ProcessEntry.KindEnum.FAILURE_HANDLER, StatusEnum.FAILED);
    }

    @Test
    public void testOnFailureDependencies() throws Exception {
        String msg = "msg_" + randomString();
        byte[] payload = archive(ProcessIT.class.getResource("onFailureDependencies").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.msg", msg);

        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForStatus(getApiClient(), spr.getInstanceId(), StatusEnum.FAILED);

        // ---

        byte[] ab = getLog(pe.getInstanceId());
        assertLogAtLeast(".*" + msg + ".*", 1, ab);

        // ---

        ProcessEntry child = waitForChild(processApi, spr.getInstanceId(), ProcessEntry.KindEnum.FAILURE_HANDLER, StatusEnum.FAILED, StatusEnum.FINISHED);
        assertEquals(StatusEnum.FINISHED, child.getStatus());

        // check the logs for the successful message

        ab = getLog(child.getInstanceId());
        assertLog(".*Hello!", ab);
    }

    @Test
    public void testCancel() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("cancelHandling").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        waitForStatus(getApiClient(), spr.getInstanceId(), StatusEnum.RUNNING);

        // cancel the running process

        processApi.kill(spr.getInstanceId());
        waitForStatus(getApiClient(), spr.getInstanceId(), StatusEnum.CANCELLED);

        // find the child processes

        ProcessEntry child = waitForChild(processApi, spr.getInstanceId(), ProcessEntry.KindEnum.CANCEL_HANDLER, StatusEnum.FINISHED);

        // check the logs for the successful message

        byte[] ab = getLog(child.getInstanceId());
        assertLog(".*initiator is admin.*", ab);
    }

    @Test
    public void testCancelSuspended() throws Exception {
        String aValue = "value_" + randomString();
        byte[] payload = archive(ProcessIT.class.getResource("cancelSuspendHandling").toURI());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.aValue", aValue);
        StartProcessResponse spr = start(input);
        waitForStatus(getApiClient(), spr.getInstanceId(), StatusEnum.SUSPENDED);

        // check if the form is there
        ProcessFormsApi processFormsApi = new ProcessFormsApi(getApiClient());
        List<FormListEntry> forms = processFormsApi.listProcessForms(spr.getInstanceId());
        assertEquals(1, forms.size());

        // cancel the suspended process

        processApi.kill(spr.getInstanceId());
        waitForStatus(getApiClient(), spr.getInstanceId(), StatusEnum.CANCELLED);

        // find the child processes

        ProcessEntry child = waitForChild(processApi, spr.getInstanceId(), ProcessEntry.KindEnum.CANCEL_HANDLER, StatusEnum.FINISHED);

        // check the logs for the successful message

        byte[] ab = getLog(child.getInstanceId());
        assertLog(".*" + aValue + " still here.*", ab);
    }

    @Test
    public void testCancelSuspendedAfterTwoForms() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("cancelSuspendAfterTwoForms").toURI());

        // start the process
        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        waitForStatus(getApiClient(), spr.getInstanceId(), StatusEnum.SUSPENDED);

        // check if the first form is there
        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());
        List<FormListEntry> forms = formsApi.listProcessForms(spr.getInstanceId());
        assertEquals(1, forms.size());

        // submit the first form
        formsApi.submitForm(spr.getInstanceId(), "myForm1", Collections.singletonMap("x", "123"));

        // wait for the process to suspend
        waitForStatus(getApiClient(), spr.getInstanceId(), StatusEnum.SUSPENDED);

        // check if the second form's ready
        forms = formsApi.listProcessForms(spr.getInstanceId());
        assertEquals(1, forms.size());

        // cancel the process
        processApi.kill(spr.getInstanceId());

        // find the child processes
        ProcessEntry child = waitForChild(processApi, spr.getInstanceId(), ProcessEntry.KindEnum.CANCEL_HANDLER,
                StatusEnum.FINISHED, StatusEnum.FAILED);
        assertEquals(StatusEnum.FINISHED, child.getStatus());

        // check the logs for the successful message
        byte[] ab = getLog(child.getInstanceId());
        assertLog(".*Hello!.*", ab);
    }

    @Test
    public void testOnFailureForForks() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("forkOnFailure").toURI());

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        List<ProcessEntry> children = processApi.listSubprocesses(pe.getInstanceId(), null);
        assertEquals(2, children.size());

        ProcessEntry childWithOnFailure = children.stream().filter(p -> p.getHandlers() != null && p.getHandlers().contains("onFailure"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Can't find a child with an onFailure handler"));

        ProcessEntry childWithoutOnFailure = children.stream().filter(p -> p.getHandlers() == null || p.getHandlers().isEmpty())
                .findFirst().orElseThrow(() -> new IllegalStateException("Can't find a child without an onFailure handler"));

        // ---

        ProcessEntry onFailureProc = waitForChild(processApi, childWithOnFailure.getInstanceId(), ProcessEntry.KindEnum.FAILURE_HANDLER, StatusEnum.FINISHED);
        byte[] ab = getLog(onFailureProc.getInstanceId());
        assertLog(".*Got.*aFork!.*", ab);

        // ---

        List<ProcessEntry> childWithoutOnFailureChildren = processApi.listSubprocesses(childWithoutOnFailure.getInstanceId(), null);
        assertEquals(0, childWithoutOnFailureChildren.size());
    }

    @Test
    void testNoHandlingForDisabledUser() throws Exception {
        // create user to be disabled

        UsersApi usersApi = new UsersApi(getApiClient());

        String userAName = "user_" + randomString();
        var user = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userAName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse apiKeyA = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userAName));

        // switch to temp user's api key

        setApiKey(apiKeyA.getKey());

        // start the process as temp user

        byte[] payload = archive(ProcessIT.class.getResource("cancelHandling").toURI());
        StartProcessResponse spr = start(payload);
        waitForStatus(getApiClient(), spr.getInstanceId(), StatusEnum.RUNNING);

        // back to admin api token, disable the user

        resetApiKey();
        usersApi.disableUser(user.getId(), true);

        // cancel the running process

        new ProcessApi(getApiClient()).kill(spr.getInstanceId());
        waitForStatus(getApiClient(), spr.getInstanceId(), StatusEnum.CANCELLED);

        // expect error starting child process

        waitForLog(spr.getInstanceId(), ".*Error while starting onCancel handler: initiator is disabled.*");
    }
}
