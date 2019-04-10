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

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.*;

public class CheckpointsIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testCheckpoint() throws Exception {
        // prepare the payload

        byte[] payload = archive(CheckpointsIT.class.getResource("checkpoints").toURI());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // check the logs

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*==Start.*", ab);
        assertLog(".*==Middle.*", ab);
        assertLog(".*==End.*", ab);

        // restore from TWO checkpoint
        restoreFromCheckpoint(pir.getInstanceId(), "TWO");

        waitForCompletion(processApi, spr.getInstanceId());

        ab = getLog(pir.getLogFileName());
        assertLog(".*==Start.*", ab);
        assertLog(".*==Middle.*", ab);
        assertLog(".*==End.*", 2, ab);

        // restore from ONE checkpoint
        restoreFromCheckpoint(pir.getInstanceId(), "ONE");

        waitForCompletion(processApi, spr.getInstanceId());
        ab = getLog(pir.getLogFileName());
        assertLog(".*==Start.*", ab);
        assertLog(".*==Middle.*", 2, ab);
        assertLog(".*==End.*", 3, ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testRestoreCheckpointWithGetByName() throws Exception {
        byte[] payload = archive(CheckpointsIT.class.getResource("oneCheckpoint").toURI());

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*aaa before.*", ab);
        assertLog(".*bbb after.*", ab);

        // ---

        CheckpointV2Api checkpointV2Api = new CheckpointV2Api(getApiClient());
        checkpointV2Api.processCheckpoint(pir.getInstanceId(), "one", "restore");

        // ---

        waitForCompletion(processApi, spr.getInstanceId());

        ab = getLog(pir.getLogFileName());
        assertLog(".*aaa before.*", ab);
        assertLog(".*bbb after.*", 2, ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testCheckpointWithError() throws Exception {
        // prepare the payload

        byte[] payload = archive(CheckpointsIT.class.getResource("checkpointsWithError").toURI());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---
        Map<String, Object> data = Collections.singletonMap("shouldFail", true);
        submitForm(spr.getInstanceId(), data);

        // ---
        ProcessEntry pir = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.FAILED);

        // check error message
        assertProcessErrorMessage(pir, "As you wish");

        // ---
        restoreFromCheckpoint(pir.getInstanceId(), "ONE");

        pir = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // no error message after process restored
        assertNoProcessErrorMessage(pir);

        // ---
        data = Collections.singletonMap("shouldFail", false);
        submitForm(spr.getInstanceId(), data);

        waitForCompletion(processApi, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*==Start.*", ab);
        assertLog(".*==Middle.*", ab);
        assertLog(".*==End.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testCheckpointWithArgs() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setAcceptsRawPayload(true));

        String value = "value_" + randomString();

        EncryptValueResponse evr = projectsApi.encrypt(orgName, projectName, value);
        assertTrue(evr.isOk());

        // prepare the payload

        byte[] payload = archive(CheckpointsIT.class.getResource("checkpointsWithArgs").toURI());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(ImmutableMap.of(
                "org", orgName,
                "project", projectName,
                "archive", payload,
                "arguments.encrypted", evr.getData()));
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*hello, World.*", 2, ab);
        assertLog(".*" + value + ".*", 4, ab);
        assertLog(".*checkpoint pointA.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testExpressions() throws Exception {
        String xValue = "x_" + randomString();

        // ---

        byte[] payload = archive(CheckpointsIT.class.getResource("checkpointExpressions").toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "arguments.x", xValue,
                "archive", payload));

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*checkpoint test_" + xValue + ".*", 1, ab);
    }

    private void restoreFromCheckpoint(UUID instanceId, String name) throws ApiException {
        CheckpointApi checkpointApi = new CheckpointApi(getApiClient());
        ProcessEventsApi processEventsApi = new ProcessEventsApi(getApiClient());
        List<ProcessEventEntry> processEvents = processEventsApi.list(instanceId, null, null,null);
        assertNotNull(processEvents);

        // restore from ONE checkpoint
        String checkpointId = assertCheckpoint(name, processEvents);

        ResumeProcessResponse resp = checkpointApi.restore(instanceId,
                new RestoreCheckpointRequest().setId(UUID.fromString(checkpointId)));

        assertNotNull(resp);
    }

    private void submitForm(UUID instanceId, Map<String, Object> data) throws ApiException {
        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(instanceId);
        assertEquals(1, forms.size());

        FormListEntry f = forms.get(0);
        FormSubmitResponse fsr = formsApi.submit(instanceId, f.getName(), data);
        assertTrue(fsr.isOk());
    }

    @SuppressWarnings("unchecked")
    private static String assertCheckpoint(String name, List<ProcessEventEntry> processEvents) {
        String checkpointDescription = "Checkpoint: " + name;

        for (ProcessEventEntry e : processEvents) {
            Map<String, Object> data = (Map<String, Object>) e.getData();
            if (data == null) {
                continue;
            }
            if ("post".equals(data.get("phase")) && checkpointDescription.equals(data.get("description"))) {
                List<Map<String, Object>> out = (List<Map<String, Object>>) data.get("out");
                if (out == null || out.size() < 2) {
                    continue;
                }

                String checkpointId = assertParam("checkpointId", out.get(0));
                String checkpointName = assertParam("checkpointName", out.get(1));
                if (name.equals(checkpointName)) {
                    return checkpointId;
                }
            }
        }
        throw new IllegalStateException("can't find " + name + " checkpoint");
    }

    private static String assertParam(String paramName, Map<String, Object> params) {
        assertEquals(paramName, params.get("source"));
        assertEquals(paramName, params.get("target"));
        assertNotNull(params.get("resolved"));
        return (String)params.get("resolved");
    }

    @SuppressWarnings("unchecked")
    private static void assertNoProcessErrorMessage(ProcessEntry p) {
        assertNotNull(p);

        Map<String, Object> meta = p.getMeta();
        if (meta == null || meta.isEmpty()) {
            return;
        }

        Map<String, Object> out = (Map<String, Object>) meta.get("out");
        if (out == null || out.isEmpty()) {
            return;
        }

        Map<String, Object> error = (Map<String, Object>) out.get(Constants.Context.LAST_ERROR_KEY);
        assertNull(error);
    }

    @SuppressWarnings("unchecked")
    private static void assertProcessErrorMessage(ProcessEntry p, String expected) {
        assertNotNull(p);

        Map<String, Object> meta = p.getMeta();
        assertNotNull(meta);

        Map<String, Object> out = (Map<String, Object>) meta.get("out");
        assertNotNull(out);

        Map<String, Object> error = (Map<String, Object>) out.get(Constants.Context.LAST_ERROR_KEY);
        assertNotNull(error);

        assertEquals(expected, error.get("message"));
    }
}
