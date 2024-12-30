package com.walmartlabs.concord.it.runtime.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.*;

import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static com.walmartlabs.concord.it.runtime.v2.Utils.resourceToString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ProcessIT extends AbstractTest {

    @RegisterExtension
    public static final ConcordRule concord = ConcordConfiguration.configure();

    /**
     * Argument passing.
     */
    @Test
    public void testArgs() throws Exception {
        Payload payload = new Payload()
                .archive(resource("args"))
                .arg("name", "Concord");

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*Runtime: concord-v2.*");
        proc.assertLog(".*Hello, Concord!.*");
    }

    /**
     * Groovy script execution.
     */
    @Test
    public void testGroovyScripts() throws Exception {
        Payload payload = new Payload()
                .archive(resource("scriptGroovy"))
                .arg("name", "Concord");

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*Runtime: concord-v2.*");
        proc.assertLog(".*log from script: 123.*");
    }

    /**
     * Js script execution.
     */
    @Test
    public void testJsScripts() throws Exception {
        Payload payload = new Payload()
                .archive(resource("scriptJs"))
                .arg("arg", "12345")
                .arg("pattern", ".234.");

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*matches: true.*");
    }

    /**
     * Ruby script execution.
     */
    @Test
    public void testRubyScripts() throws Exception {
        Payload payload = new Payload()
                .archive(resource("scriptRuby"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*scriptTask: A1:A2*");
        proc.assertLog(".*scriptTask: B1:B2*");
        proc.assertLog(".*scriptTask: result: A1-Ruby*");
        proc.assertLog(".*scriptTask: result: B1-Ruby*");
    }

    /**
     * Test the process metadata.
     */
    @Test
    public void testMetaUpdate() throws Exception {
        Payload payload = new Payload()
                .archive(resource("meta"))
                .arg("name", "Concord");

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        proc.assertLog(".*Runtime: concord-v2.*");
        proc.assertLog(".*Hello, Concord!.*");

        assertNotNull(pe.getMeta());
        assertEquals(4, pe.getMeta().size()); // 2 + plus system meta + entryPoint
        assertEquals("init-value", pe.getMeta().get("test"));
        assertEquals("xxx", pe.getMeta().get("myForm.action"));
        assertEquals("default", pe.getMeta().get("entryPoint"));

        // ---

        List<FormListEntry> forms = proc.forms();
        assertEquals(1, forms.size());

        Map<String, Object> data = new HashMap<>();
        data.put("action", "Reject");

        FormSubmitResponse fsr = proc.submitForm(forms.get(0).getName(), data);
        assertTrue(fsr.getOk());

        pe = expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*Action: Reject.*");

        assertNotNull(pe.getMeta());
        assertEquals(4, pe.getMeta().size()); // 2 + plus system meta + entryPoint
        assertEquals("init-value", pe.getMeta().get("test"));
        assertEquals("Reject", pe.getMeta().get("myForm.action"));
        assertEquals("default", pe.getMeta().get("entryPoint"));
    }

    /**
     * Test the process metadata with exit step.
     */
    @Test
    public void testMetaWithExit() throws Exception {
        Payload payload = new Payload()
                .archive(resource("exitWithMeta"))
                .arg("name", "Concord");

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*Hello, Concord!.*");

        assertNotNull(pe.getMeta());
        assertEquals("init-value", pe.getMeta().get("test"));
    }

    @Test
    public void testOutVariables() throws Exception {
        Payload payload = new Payload()
                .archive(resource("out"))
                .out("x", "y.some.boolean", "z");

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        Map<String, Object> data = proc.getOutVariables();
        assertNotNull(data);

        assertEquals(123, data.get("x"));
        assertEquals(true, data.get("y.some.boolean"));
        assertFalse(data.containsKey("z"));
    }

    @Test
    public void testOutVariablesForFailedProcess() throws Exception {
        Payload payload = new Payload()
                .archive(resource("outForFailed"))
                .out("x", "y.some.boolean", "z");

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FAILED);

        // ---

        Map<String, Object> data = proc.getOutVariables();
        assertNotNull(data);

        assertEquals(123, data.get("x"));
        assertEquals(true, data.get("y.some.boolean"));
        assertFalse(data.containsKey("z"));
    }

    @Test
    public void testThrowWithPayload() throws Exception {
        Payload payload = new Payload()
                .archive(resource("throwWithPayload"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FAILED);

        // ---

        Map<String, Object> data = proc.getOutVariables();
        assertNotNull(data);

        assertEquals("BOOM", ConfigurationUtils.get(data, "lastError", "message"));
        assertEquals(Map.of("key", "value", "key2", "value2"), ConfigurationUtils.get(data, "lastError", "payload"));
    }

    @Test
    public void testLogsFromExpressions() throws Exception {
        Payload payload = new Payload()
                .archive(resource("logExpression"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*log from expression short.*");
        proc.assertLog(".*log from expression full form.*");
    }

    @Test
    public void testProjectInfo() throws Exception {
        String orgName = "org_" + randomString();
        concord.organizations().create(orgName);

        String projectName = "project_" + randomString();
        concord.projects().create(orgName, projectName);

        Payload payload = new Payload()
                .org(orgName)
                .project(projectName)
                .archive(resource("projectInfo"));

        ConcordProcess proc = concord.processes().start(payload);

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*orgName=" + orgName + ".*");
        proc.assertLog(".*projectName=" + projectName + ".*");
    }

    @Test
    public void testCheckpoints() throws Exception {
        Payload payload = new Payload()
                .archive(resource("checkpoints"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*#1.*x=123.*");
        proc.assertLog(".*#2.*y=234.*");
        proc.assertLog(".*#3.*y=345.*");
        proc.assertLog(".*same workDir: true.*");

        // ---

        List<ProcessCheckpointEntry> checkpoints = proc.checkpoints();
        assertEquals(2, checkpoints.size());

        checkpoints.sort(Comparator.comparing(ProcessCheckpointEntry::getCreatedAt));

        ProcessCheckpointEntry firstCheckpoint = checkpoints.get(0);
        assertEquals("first", firstCheckpoint.getName());

        ProcessCheckpointEntry secondCheckpoint = checkpoints.get(1);
        assertEquals("second", secondCheckpoint.getName());

        // ---

        // restore from the first checkpoint

        proc.restoreCheckpoint(firstCheckpoint.getId());
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // we should see the second checkpoint being saved the second time

        checkpoints = proc.checkpoints();
        assertEquals(3, checkpoints.size());

        checkpoints.sort(Comparator.comparing(ProcessCheckpointEntry::getCreatedAt));

        assertEquals("second", checkpoints.get(1).getName());
        assertEquals("second", checkpoints.get(2).getName());

        proc.assertLog(".*#1.*x=123.*");
        proc.assertLogAtLeast(".*#3.*y=345.*", 2);
        proc.assertLog(".*same workDir: false.*");
    }

    @Test
    public void testCheckpointsParallel() throws Exception {
        Payload payload = new Payload()
                .archive(resource("checkpointsParallel"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);
        proc.assertLogAtLeast(".*#1 \\{x=123}.*", 1);
        proc.assertLogAtLeast(".*#2 \\{x=123, y=234}.*", 1);
        proc.assertLogAtLeast(".*#3 \\{x=123, z=345}.*", 1);
        proc.assertLogAtLeast(".*#4 \\{x=123}.*", 1);

        // ---

        List<ProcessCheckpointEntry> checkpoints = proc.checkpoints();
        assertEquals(3, checkpoints.size());

        checkpoints.sort(Comparator.comparing(ProcessCheckpointEntry::getName));
        assertEquals("aaa", checkpoints.get(0).getName());
        assertEquals("bbb", checkpoints.get(1).getName());
        assertEquals("ccc", checkpoints.get(2).getName());

        // ---

        proc.restoreCheckpoint(checkpoints.get(1).getId());
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLogAtLeast(".*#4 \\{x=123}.*", 2);
    }

    @Test
    public void testNoStateAfterCheckpoint() throws Exception {
        String concordYml = resourceToString(ProcessIT.class.getResource("checkpointState/concord.yml"))
                .replaceAll("PROJECT_VERSION", ITConstants.PROJECT_VERSION);

        Payload payload = new Payload().concordYml(concordYml);

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        List<ProcessCheckpointEntry> checkpoints = proc.checkpoints();
        assertEquals(1, checkpoints.size());

        proc.assertLog(".*#1 BEFORE: false.*");
        proc.assertLog(".*#2 AFTER: false.*");
    }

    @Test
    public void testForkCheckpoints() throws Exception {
        String forkTag = "fork_" + randomString();

        Payload payload = new Payload()
                .arg("forkTag", forkTag)
                .archive(resource("forkCheckpoints"));

        ConcordProcess parent = concord.processes().start(payload);
        expectStatus(parent, ProcessEntry.StatusEnum.FINISHED);
        parent.assertLog(".*#1.*");
        parent.assertLog(".*#2.*");

        // ---

        List<ProcessEntry> children = concord.processes().list(ProcessListFilter.builder()
                .parentInstanceId(parent.instanceId())
                .limit(10)
                .build());

        assertEquals(1, children.size());

        ProcessEntry fork = children.get(0);
        assertEquals(fork.getTags().iterator().next(), forkTag);

        // ---

        List<ProcessCheckpointEntry> checkpoints = parent.checkpoints();
        assertEquals(1, checkpoints.size());

        parent.restoreCheckpoint(checkpoints.get(0).getId());
        expectStatus(parent, ProcessEntry.StatusEnum.FINISHED);

        // ---

        children = concord.processes().list(ProcessListFilter.builder()
                .parentInstanceId(parent.instanceId())
                .limit(10)
                .build());

        assertEquals(2, children.size());

        // ---

        for (ProcessEntry child : children) {
            ConcordProcess proc = concord.processes().get(child.getInstanceId());
            proc.assertNoLog(".*#1.*");
            proc.assertNoLog(".*#2.*");
            proc.assertLog(".*#3.*");
        }
    }

    @Test
    public void testCheckpointsWith3rdPartyClasses() throws Exception {
        String concordYml = resourceToString(NodeRosterIT.class.getResource("checkpointClasses/concord.yml"))
                .replaceAll("PROJECT_VERSION", ITConstants.PROJECT_VERSION);

        ConcordProcess proc = concord.processes().start(new Payload()
                .concordYml(concordYml));

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        List<ProcessCheckpointEntry> checkpoints = proc.checkpoints();
        assertEquals(1, checkpoints.size());

        proc.restoreCheckpoint(checkpoints.get(0).getId());
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*1: Hello!.*");
        proc.assertLogAtLeast(".*2: Hello!.*", 2);
    }

    @Test
    public void testLastErrorSave() throws Exception {
        Payload payload = new Payload()
                .archive(resource("failProcess"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FAILED);

        // ---

        Map<String, Object> data = proc.getOutVariables();
        assertNotNull(data);
        Map<String, Object> m = new HashMap<>();
        m.put("@id", 1);
        m.put("message", "BOOM");
        assertEquals(m, data.get("lastError"));
    }

    @Test
    public void testSuspendTimeoutFromPayload() throws Exception {
        Payload payload = new Payload()
                .parameter("suspendTimeout", "PT1S")
                .archive(resource("form"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.TIMED_OUT);
    }

    @Test
    public void testSuspendTimeout() throws Exception {
        Payload payload = new Payload()
                .archive(resource("formWithTimeout"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.TIMED_OUT);
    }

    @Test
    public void testYamlRootFile() throws Exception {
        Payload payload = new Payload()
                .archive(resource("yamlRootFile"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);
        proc.assertLog(".*Hello, Concord!*");
    }

    @Test
    public void testMetadataWithWithItems() throws Exception {
        ConcordProcess proc = concord.processes().start(new Payload()
                .archive(resource("processMetadataWithItems")));

        ProcessEntry pe = expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);
        assertNotNull(pe.getMeta());
        assertEquals("c", pe.getMeta().get("var"));
    }

    @Test
    public void testMetadataUpdateOnlyOnEnd() throws Exception {
        ConcordProcess proc = concord.processes().start(new Payload()
                .activeProfiles("disableMetaUpdates")
                .archive(resource("processMetadataSend")));

        ProcessEntry pe = proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);
        assertNotNull(pe.getMeta());
        assertEquals("c", pe.getMeta().get("var"));

        // expect one update
        int sendCount = proc.getLogLines(line -> line.matches(".*sending process meta.*")).size();
        assertEquals(1, sendCount);
    }

    @Test
    public void testMetadataUpdateSuspendAndEnd() throws Exception {
        ConcordProcess proc = concord.processes().start(new Payload()
                .activeProfiles("disableMetaUpdates")
                .arg("doSuspend", true)
                .archive(resource("processMetadataSend")));

        ProcessEntry pe = proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);
        assertNotNull(pe.getMeta());
        assertEquals("c", pe.getMeta().get("var"));

        // expect two updates (one on suspend, one on finish)
        int sendCount = proc.getLogLines(line -> line.matches(".*sending process meta.*")).size();
        assertEquals(2, sendCount);
    }

    @Test
    public void testMetadataUpdateDefault() throws Exception {
        ConcordProcess proc = concord.processes().start(new Payload()
                .archive(resource("processMetadataSend")));

        ProcessEntry pe = proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);
        assertNotNull(pe.getMeta());
        assertEquals("c", pe.getMeta().get("var"));

        // expect five updates (three set calls in a loop, plus two more)
        int sendCount = proc.getLogLines(line -> line.matches(".*sending process meta.*")).size();
        assertEquals(5, sendCount);
    }

    @Test
    public void testEmptyExclusiveGroup() throws Exception {
        ConcordProcess proc = concord.processes().start(new Payload()
                .archive(resource("emptyExclusiveGroup")));

        expectStatus(proc, ProcessEntry.StatusEnum.FAILED);
        proc.assertLog(".*Invalid exclusive mode.*");
    }

    @Test
    public void testNullCallInputParam() throws Exception {
        ConcordProcess proc = concord.processes().start(new Payload()
                .archive(resource("nullCallInputParam")));

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);
        proc.assertLog(".*nullParam: ''.*");
    }

    @Test
    public void testForkVariablesAfterForm() throws Exception {
        ConcordProcess proc = concord.processes().start(new Payload()
                .archive(resource("forkAfterForm")));

        expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        proc.submitForm("myForm", Collections.singletonMap("name", "test"));

        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

        ProcessEntry forkEntry = proc.waitForChildStatus(ProcessEntry.StatusEnum.FINISHED);
        ConcordProcess fork = concord.processes().get(forkEntry.getInstanceId());

        fork.assertLog(".*parentInstanceId: " + proc.instanceId() + ".*");
        fork.assertLog(".*txId: " + fork.instanceId() + ".*");
    }

    @Test
    public void testTaskWithClient1() throws Exception {
        String concordYml = resourceToString(ProcessIT.class.getResource("client1Task/concord.yml"))
                .replaceAll("PROJECT_VERSION", ITConstants.PROJECT_VERSION);

        Payload payload = new Payload().concordYml(concordYml);

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*process entry: RUNNING.*");
        proc.assertLog(".*Works!.*");
    }

    @Test
    public void testRestart() throws Exception {
        Payload payload = new Payload()
                .archive(resource("args"))
                .arg("name", "Concord");

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*Runtime: concord-v2.*");
        proc.assertLog(".*Hello, Concord!.*");

        // restart
        ProcessApi processApi = new ProcessApi(concord.apiClient());
        processApi.restartProcess(proc.instanceId());

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLogAtLeast(".*Runtime: concord-v2.*", 2);
        proc.assertLogAtLeast(".*Hello, Concord!.*", 2);

        // ---
        ProcessEventsApi processEventsApi = new ProcessEventsApi(concord.apiClient());
        List<ProcessEventEntry> events = processEventsApi.listProcessEvents(proc.instanceId(), "PROCESS_STATUS", null, null, null, null, null, null);
        assertNotNull(events);

        // 2 NEW events
        long eventsCount = events.stream().filter(e -> "NEW".equals(MapUtils.assertString(e.getData(), "status"))).count();
        assertEquals(2, eventsCount, "" + events);

        // empty wait conditions
        ProcessWaitEntry waitConditions = processApi.getWait(proc.instanceId());
        assertFalse(waitConditions.getIsWaiting());
        assertNull(waitConditions.getWaits());
    }

    @Test
    public void metaAfterSuspend() throws Exception {
        Payload payload = new Payload()
                .archive(resource("metaAfterSuspend"));

        ConcordProcess proc = concord.processes().start(payload);
        ProcessEntry pe = expectStatus(proc, ProcessEntry.StatusEnum.FAILED);

        // ---
        Object myMetaValue = pe.getMeta().get("myMetaVar");
        assertEquals("myMetaVarValue", myMetaValue);
    }

    /**
     * Tests process event batch flushing when a long-running task executes.
     */
    @Test
    public void testEventBatchingShortTimer() throws Exception {
        Payload payload = new Payload()
                .activeProfiles("shortFlush")
                .archive(resource("eventBatchingTimer"));

        ConcordProcess proc = concord.processes().start(payload);
        ProcessEntry pe = expectStatus(proc, ProcessEntry.StatusEnum.RUNNING);

        // let it run at least long enough to report an event batch (1-second interval)
        Thread.sleep(1_500);

        // At this point the process is still executing the sleep task.
        // We set a 1-second batch duration, so we can not expect a batch to have
        // been reported even though the max batch size (100) was not met.

        // ---
        List<ProcessEventEntry> events = getProcessElementEvents(proc);

        // clean up
        new ProcessApi(concord.apiClient()).kill(pe.getInstanceId());

        // ---
        assertNotNull(events);
        assertFalse(events.isEmpty());
        assertEquals(1, events.size());

        ProcessEventEntry sleepEvent = events.get(0);

        assertEquals("sleep", sleepEvent.getData().get("name"));
    }

    /**
     * Demonstrates what happens if process event batching flush timer is too long,
     * or effectively doesn't exist.
     */
    @Test
    public void testEventBatchingLongTimer() throws Exception {
        Payload payload = new Payload()
                .activeProfiles("longFlush")
                .archive(resource("eventBatchingTimer"));

        ConcordProcess proc = concord.processes().start(payload);
        ProcessEntry pe = expectStatus(proc, ProcessEntry.StatusEnum.RUNNING);

        // let it run long enough to prove events aren't going to update any time soon
        Thread.sleep(1_500);

        // ---
        List<ProcessEventEntry> events = getProcessElementEvents(proc);
        assertNotNull(events);
        // No events because batch is still waiting to get large enough to report
        assertTrue(events.isEmpty());

        // clean up
        new ProcessApi(concord.apiClient()).kill(pe.getInstanceId());
    }

    /**
     * Executes a flow that will over-fill process event queue if not properly synchronized
     */
    @Test
    public void testEventBatchingParallel() throws Exception {
        Payload payload = new Payload()
                .archive(resource("eventBatchingParallel"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---
        List<ProcessEventEntry> events = getProcessElementEvents(proc);
        assertNotNull(events);
        assertFalse(events.isEmpty());
    }

    @Test
    public void testSimpleDryRun() throws Exception {
        Payload payload = new Payload()
                .parameter(Constants.Request.DRY_RUN_MODE_KEY, true)
                .archive(resource("dryRun"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".* Running in dry-run mode: Skipping sending request.*");
        proc.assertLog(".* isDryRun: true.*");
    }

    @Test
    public void testDryRunModeNotSupportedByScript() throws Exception {
        Payload payload = new Payload()
                .parameter(Constants.Request.DRY_RUN_MODE_KEY, true)
                .archive(resource("scriptJs"))
                .arg("arg", "12345")
                .arg("pattern", ".234.");

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FAILED);

        proc.assertLog(".*Error @ line: 6, col: 7. Dry-run mode is not supported for this 'script' step.*");
    }

    @Test
    public void testThrowParallelWithPayload() throws Exception {
        Payload payload = new Payload()
                .archive(resource("parallelExceptionPayload"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---
        Map<String, Object> data = proc.getOutVariables();
        List<Map<String, Object>> exceptions = (List<Map<String, Object>>) ConfigurationUtils.get(data, "exceptions");

        assertNotNull(exceptions);
        assertEquals(List.of("BOOM1", "BOOM2"), exceptions.stream().map(e -> e.get("message")).toList());
        assertEquals(List.of(Map.of("key", 1), Map.of("key", 2)), exceptions.stream().map(e -> e.get("payload")).toList());
    }

    private List<ProcessEventEntry> getProcessElementEvents(ConcordProcess proc) throws Exception {
        ProcessEventsApi processEventsApi = new ProcessEventsApi(concord.apiClient());
        return processEventsApi.listProcessEvents(proc.instanceId(), "ELEMENT", null, null, null, "pre", null, null);
    }

}
