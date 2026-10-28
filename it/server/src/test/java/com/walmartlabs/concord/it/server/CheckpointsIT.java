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
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.*;

public class CheckpointsIT extends AbstractServerIT {

    @Test
    public void testCheckpoint() throws Exception {
        // prepare the payload

        byte[] payload = archive(CheckpointsIT.class.getResource("checkpoints").toURI());

        // start the process

        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // check the logs

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*==Start.*", ab);
        assertLog(".*==Middle.*", ab);
        assertLog(".*==End.*", ab);

        // restore from TWO checkpoint
        restoreFromCheckpoint(pir.getInstanceId(), "TWO");

        waitForCompletion(getApiClient(), spr.getInstanceId());

        ab = getLog(pir.getInstanceId());

        assertLog(".*==Start.*", ab);
        assertLog(".*==Middle.*", ab);
        assertLog(".*==End.*", 2, ab);

        // restore from ONE checkpoint
        restoreFromCheckpoint(pir.getInstanceId(), "ONE");

        waitForCompletion(getApiClient(), spr.getInstanceId());
        ab = getLog(pir.getInstanceId());

        assertLog(".*==Start.*", ab);
        assertLog(".*==Middle.*", 2, ab);
        assertLog(".*==End.*", 3, ab);
    }

    @Test
    public void testRestoreCheckpointWithGetByName() throws Exception {
        byte[] payload = archive(CheckpointsIT.class.getResource("oneCheckpoint").toURI());

        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*aaa before.*", ab);
        assertLog(".*bbb after.*", ab);

        // ---

        CheckpointV2Api checkpointV2Api = new CheckpointV2Api(getApiClient());
        checkpointV2Api.processCheckpoint(pir.getInstanceId(), "one", "restore");

        // ---

        waitForCompletion(getApiClient(), spr.getInstanceId());

        ab = getLog(pir.getInstanceId());
        assertLog(".*aaa before.*", ab);
        assertLog(".*bbb after.*", 2, ab);
    }

    @Test
    public void testRestoreCheckpointWithEventNameV2() throws Exception {
        testRestoreCheckpointWithEventName("checkpointsWithEventNameV2");
    }

    private void testRestoreCheckpointWithEventName(String resource) throws Exception {
        // prepare the payload

        byte[] payload = archive(CheckpointsIT.class.getResource(resource).toURI());

        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // restore from first checkpoint
        CheckpointV2Api checkpointV2Api = new CheckpointV2Api(getApiClient());
        checkpointV2Api.processCheckpoint(pir.getInstanceId(), "first", "restore");

        // ---

        waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Event Name: first.*", ab);

        // restore from second checkpoint
        checkpointV2Api.processCheckpoint(pir.getInstanceId(), "second", "restore");

        waitForCompletion(getApiClient(), spr.getInstanceId());
        ab = getLog(pir.getInstanceId());
        assertLog(".*Event Name: second.*", ab);
    }

    @Test
    public void testCheckpointWithError() throws Exception {
        // prepare the payload

        byte[] payload = archive(CheckpointsIT.class.getResource("checkpointsWithError").toURI());

        // start the process

        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---
        Map<String, Object> data = Collections.singletonMap("shouldFail", true);
        submitForm(spr.getInstanceId(), data);

        // ---
        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FAILED);

        // check error message
        assertProcessErrorMessage(pir, "As you wish");

        // ---
        restoreFromCheckpoint(pir.getInstanceId(), "ONE");

        pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // no error message after process restored
        assertNoProcessErrorMessage(pir);

        // ---
        data = Collections.singletonMap("shouldFail", false);
        submitForm(spr.getInstanceId(), data);

        waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*==Start.*", ab);
        assertLog(".*==Middle.*", ab);
        assertLog(".*==End.*", ab);
    }

    @Test
    public void testCheckpointWithArgs() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        String value = "value_" + randomString();

        EncryptValueResponse evr = projectsApi.encrypt(orgName, projectName, value);
        assertTrue(evr.getOk());

        // prepare the payload

        byte[] payload = archive(CheckpointsIT.class.getResource("checkpointsWithArgs").toURI());

        // start the process

        StartProcessResponse spr = start(ImmutableMap.of(
                "org", orgName,
                "project", projectName,
                "archive", payload,
                "arguments.encrypted", evr.getData(),
                "arguments.expected", value));
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*hello, World.*", 2, ab);
        assertLog(".*varB matches: true.*", 2, ab);
        assertLog(".*keyVar matches: true.*", 2, ab);
        assertLog(".*Checkpoint 'pointA' created.*", ab);

        restoreFromCheckpoint(pir.getInstanceId(), "pointA");
        waitForCompletion(getApiClient(), spr.getInstanceId());
        ab = getLog(pir.getInstanceId());
        assertLog(".*hello, World.*", 3, ab);
        assertLog(".*varB matches: true.*", 3, ab);
        assertLog(".*keyVar matches: true.*", 3, ab);
    }

    @Test
    public void testExpressions() throws Exception {
        String xValue = "x_" + randomString();

        // ---

        byte[] payload = archive(CheckpointsIT.class.getResource("checkpointExpressions").toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "arguments.x", xValue,
                "archive", payload));

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Checkpoint 'test " + xValue + "' created.*", 1, ab);
        restoreFromCheckpoint(pir.getInstanceId(), "test " + xValue);
        waitForCompletion(getApiClient(), spr.getInstanceId());
        ab = getLog(pir.getInstanceId());
        assertLog(".*before the checkpoint.*", 1, ab);
        assertLog(".*after the checkpoint.*", 2, ab);
    }

    @Test
    public void testCheckpointEvents() throws Exception {
        byte[] payload = archive(CheckpointsIT.class.getResource("checkpoints").toURI());

        // ---

        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        assertCheckpointEvent(pir.getInstanceId(), "ONE", "Checkpoint: ONE");
        assertCheckpointEvent(pir.getInstanceId(), "TWO", "Checkpoint: TWO");
    }

    private void restoreFromCheckpoint(UUID instanceId, String name) throws ApiException {
        CheckpointApi checkpointApi = new CheckpointApi(getApiClient());
        UUID checkpointId = new CheckpointV3Api(getApiClient()).pageCheckpoints(instanceId, 0, 10).stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("can't find checkpoint: " + name))
                .getId();

        ResumeProcessResponse resp = checkpointApi.restore(instanceId,
                new RestoreCheckpointRequest().id(checkpointId));

        assertNotNull(resp);
    }

    private void submitForm(UUID instanceId, Map<String, Object> data) throws ApiException {
        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.listProcessForms(instanceId);
        assertEquals(1, forms.size());

        FormListEntry f = forms.get(0);
        FormSubmitResponse fsr = formsApi.submitForm(instanceId, f.getName(), data);
        assertTrue(fsr.getOk());
    }

    private void assertCheckpointEvent(UUID instanceId, String name, String description) throws ApiException {
        var checkpoint = new CheckpointV3Api(getApiClient()).pageCheckpoints(instanceId, 0, 10).stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("can't find checkpoint: " + name));
        assertNotNull(checkpoint.getCorrelationId());

        List<ProcessEventEntry> events = new ProcessEventsApi(getApiClient())
                .listProcessEvents(instanceId, "ELEMENT", null, null, null, null, true, null);
        assertTrue(events.stream().map(ProcessEventEntry::getData)
                .filter(data -> data != null)
                .anyMatch(data -> description.equals(data.get("description"))
                        && checkpoint.getCorrelationId().toString().equals(String.valueOf(data.get("correlationId")))),
                "missing checkpoint event for " + name);
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
