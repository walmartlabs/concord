package com.walmartlabs.concord.it.runtime.v25;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import com.walmartlabs.concord.client2.CheckpointApi;
import com.walmartlabs.concord.client2.FormListEntry;
import com.walmartlabs.concord.client2.FormSubmitResponse;
import com.walmartlabs.concord.client2.LogSegment;
import com.walmartlabs.concord.client2.PolicyApi;
import com.walmartlabs.concord.client2.PolicyEntry;
import com.walmartlabs.concord.client2.PolicyLinkEntry;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.client2.ProcessCheckpointEntry;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.ProcessLogV2Api;
import com.walmartlabs.concord.client2.ProcessV2Api;
import com.walmartlabs.concord.it.common.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Runtime25IT {

    private static final Duration STATUS_TIMEOUT = Duration.ofMinutes(1);
    private static final Path PERSISTENT_WORK_DIR = persistentWorkDirectory();

    @RegisterExtension
    public static final ConcordRule concord = new ConcordRule()
            .dbImage(System.getProperty("db.image", "library/postgres:14"))
            .serverImage(System.getProperty("server.image", "walmartlabs/concord-server"))
            .agentImage(System.getProperty("agent.image", "walmartlabs/concord-agent"))
            .streamServerLogs(true)
            .streamAgentLogs(true)
            .extraConfigurationSupplier(() -> "concord-server { imports { disabledProcessors = [] } }\n" +
                    "concord-agent { imports { disabledProcessors = [] } }")
            .useLocalMavenRepository(true)
            .persistentWorkDir(PERSISTENT_WORK_DIR);
    @Test
    public void runsOrderIndependentTaskCallRemotely() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("flow"), "flow resource");
        var process = concord.processes().start(new Payload().archive(resource.toURI()));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FINISHED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, result.getStatus(), processLog);
        process.assertLog(".*runtime-v2.5-remote-ok.*");
        process.assertLog(".*marker=set-ok answer=42 script=43 nested=43 loop=\\[1, 2, 3\\] currentUser=true.*");
    }

    @Test
    public void runsSdkTaskFromPackagedRuntime() throws Exception {
        var process = concord.processes().start(new Payload().concordYml(
                resourceText("sdk-task/concord.yml").replace("PROJECT_VERSION", Version.PROJECT_VERSION)));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FINISHED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, result.getStatus(), processLog);
        process.assertLog(".*SchemaTestTask: message=hello, count=5.*");
        process.assertLog(".*v25-sdk-task result=hello.*");
    }

    @Test
    public void runsProfilesAndResourcesRemotely() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("profiles-resources"),
                "profiles-resources resource");
        var process = concord.processes().start(new Payload()
                .activeProfiles("remote")
                .archive(resource.toURI()));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FINISHED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, result.getStatus(), processLog);
        process.assertLog(".*v25-imported-flow profile=remote.*");
        process.assertLog(".*v25-profile-resource profile=remote resource=imported-resource-ok.*");
    }

    @Test
    public void runsParallelBranchesRemotely() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("parallel"), "parallel resource");
        var process = concord.processes().start(new Payload().archive(resource.toURI()));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FINISHED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, result.getStatus(), processLog);
        process.assertLog(".*v25-parallel left=left right=right.*");
    }

    @Test
    public void persistsRequestedProcessOutputsRemotely() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("outputs"), "outputs resource");
        var process = concord.processes().start(new Payload()
                .archive(resource.toURI())
                .out("answer", "nested.value", "missing"));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FINISHED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, result.getStatus(), processLog);

        var output = process.getOutVariables();
        assertEquals(42, output.get("answer"));
        assertEquals("remote", output.get("nested.value"));
        assertFalse(output.containsKey("missing"));
    }

    @Test
    public void resumesParallelSuspensionsInReverseOrderRemotely() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("parallel-suspend"),
                "parallel-suspend resource");
        var process = concord.processes().start(new Payload().archive(resource.toURI()));
        var suspended = waitForStatus(process, ProcessEntry.StatusEnum.SUSPENDED);
        assertEquals(ProcessEntry.StatusEnum.SUSPENDED, suspended.getStatus());

        var processApi = new ProcessApi(concord.apiClient());
        processApi.resume(process.instanceId(), "second-event", null,
                Map.of("arguments", Map.of("secondAnswer", "second")));
        suspended = waitForStatus(process, ProcessEntry.StatusEnum.SUSPENDED);
        assertEquals(ProcessEntry.StatusEnum.SUSPENDED, suspended.getStatus());

        processApi.resume(process.instanceId(), "first-event", null,
                Map.of("arguments", Map.of("firstAnswer", "first")));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FINISHED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, result.getStatus(), processLog);
        process.assertLog(".*v25-parallel-resume first=first second=second.*");
    }

    @Test
    public void restoresNamedCheckpointWithoutReplayingCompletedWorkRemotely() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("checkpoint"), "checkpoint resource");
        var process = concord.processes().start(new Payload().archive(resource.toURI()));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FINISHED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, result.getStatus(), processLog);

        var checkpoints = process.checkpoints();
        assertEquals(1, checkpoints.size());
        ProcessCheckpointEntry checkpoint = checkpoints.get(0);
        assertEquals("stable", checkpoint.getName());
        process.restoreCheckpoint(checkpoint.getId());

        result = waitForStatus(process, ProcessEntry.StatusEnum.FINISHED);
        processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, result.getStatus(), processLog);
        assertEquals(1L, process.getLogLines().stream()
                .filter(line -> line.contains("v25-checkpoint before"))
                .count(), processLog);
        assertEquals(2L, process.getLogLines().stream()
                .filter(line -> line.contains("v25-checkpoint after"))
                .count(), processLog);
        assertEquals(1L, process.checkpoints().stream()
                .filter(item -> "stable".equals(item.getName()))
                .count());
    }

    @Test
    public void resumesFormWithSubmittedStateRemotely() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("form"), "form resource");
        var process = concord.processes().start(new Payload().archive(resource.toURI()));
        var suspended = waitForStatus(process, ProcessEntry.StatusEnum.SUSPENDED);
        assertEquals(ProcessEntry.StatusEnum.SUSPENDED, suspended.getStatus());

        var forms = process.forms();
        assertEquals(1, forms.size());
        FormListEntry form = forms.get(0);
        assertEquals("approval", form.getName());
        FormSubmitResponse submission = process.submitForm(form.getName(), Map.of("choice", "approved"));
        assertTrue(submission.getOk(), String.valueOf(submission.getErrors()));
        assertTrue(submission.getErrors() == null || submission.getErrors().isEmpty());

        var result = waitForStatus(process, ProcessEntry.StatusEnum.FINISHED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, result.getStatus(), processLog);
        process.assertLog(".*v25-form choice=approved.*");
    }

    @Test
    public void preservesParentVariablesInCancellationHandlerRemotely() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("cancel"), "cancel resource");
        var process = concord.processes().start(new Payload().archive(resource.toURI()));
        var suspended = waitForStatus(process, ProcessEntry.StatusEnum.SUSPENDED);
        assertEquals(ProcessEntry.StatusEnum.SUSPENDED, suspended.getStatus());
        process.kill();
        var cancelled = waitForStatus(process, ProcessEntry.StatusEnum.CANCELLED);
        assertEquals(ProcessEntry.StatusEnum.CANCELLED, cancelled.getStatus());

        var handler = concord.processes().get(waitForChildStatus(process, ProcessEntry.StatusEnum.FINISHED)
                .getInstanceId());
        handler.assertLog(".*v25-cancel-handler marker=parent-state.*");
    }

    @Test
    public void reportsUnhandledFailureWithSourceLocation() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("failure"), "failure resource");
        var process = concord.processes().start(new Payload().archive(resource.toURI()));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FAILED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FAILED, result.getStatus(), processLog);
        process.assertLog(".*V25_STEP_FAILED: unhandled-v2.5-error.*");
        process.assertLog(".*at concord.yml:10:7.*");
        assertEquals(1L, process.getLogLines().stream()
                .filter(line -> line.contains("V25_STEP_FAILED: unhandled-v2.5-error"))
                .count(), processLog);
        process.assertNoLog(".*Process failed.*handled-v2.5-error.*");
    }

    @Test
    public void preservesWorkspacePolicyWhenRestoringCheckpointRemotely() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("checkpoint-policy"),
                "checkpoint-policy resource");
        var orgName = "runtime25-checkpoint-" + UUID.randomUUID();
        var projectName = "policy";
        concord.organizations().create(orgName);
        concord.projects().create(orgName, projectName);

        var policyName = "runtime25-checkpoint-policy-" + UUID.randomUUID();
        var policyApi = new PolicyApi(concord.apiClient());
        policyApi.createOrUpdatePolicy(new PolicyEntry().name(policyName).rules(Map.of(
                "task", Map.of("deny", List.of(Map.of("taskName", "log", "method", "execute"))))));
        policyApi.linkPolicy(policyName, new PolicyLinkEntry().orgName(orgName).projectName(projectName));

        var process = concord.processes().start(new Payload()
                .archive(resource.toURI())
                .org(orgName)
                .project(projectName));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FAILED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FAILED, result.getStatus(), processLog);
        process.assertLog(".*Task call 'log.execute' is forbidden by task policy.*");

        var checkpoints = process.checkpoints();
        assertEquals(1, checkpoints.size());
        ProcessCheckpointEntry checkpoint = checkpoints.get(0);
        assertEquals("policy-ready", checkpoint.getName());
        process.restoreCheckpoint(checkpoint.getId());

        result = waitForStatus(process, ProcessEntry.StatusEnum.FAILED);
        processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FAILED, result.getStatus(), processLog);
        assertEquals(2L, process.getLogLines().stream()
                .filter(line -> line.contains("Task call 'log.execute' is forbidden by task policy"))
                .count(), processLog);
    }

    @Test
    public void resumesNamedLogSegmentAfterFormSubmissionRemotely() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("log-segment"),
                "log-segment resource");
        var process = concord.processes().start(new Payload().archive(resource.toURI()));
        var suspended = waitForStatus(process, ProcessEntry.StatusEnum.SUSPENDED);
        assertEquals(ProcessEntry.StatusEnum.SUSPENDED, suspended.getStatus());

        var logApi = new ProcessLogV2Api(concord.apiClient());
        var segment = logSegment(logApi.processLogSegments(process.instanceId(), 100, 0), "approval-segment");
        assertEquals(LogSegment.StatusEnum.SUSPENDED, segment.getStatus());

        var submission = process.submitForm("approval", Map.of("choice", "approved"));
        assertTrue(submission.getOk(), String.valueOf(submission.getErrors()));
        var segmentDeadline = System.nanoTime() + STATUS_TIMEOUT.toNanos();
        while (true) {
            segment = logSegment(logApi.processLogSegments(process.instanceId(), 100, 0), "approval-segment");
            if (segment.getStatus() == LogSegment.StatusEnum.RUNNING
                    || segment.getStatus() == LogSegment.StatusEnum.OK) {
                break;
            }
            if (System.nanoTime() >= segmentDeadline) {
                throw new AssertionError("Timed out after " + STATUS_TIMEOUT
                        + " waiting for segment 'approval-segment' to resume; last status: " + segment.getStatus());
            }
            sleepBeforeNextStatusPoll();
        }

        var result = waitForStatus(process, ProcessEntry.StatusEnum.FINISHED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, result.getStatus(), processLog);

        var segments = logApi.processLogSegments(process.instanceId(), 100, 0);
        segment = logSegment(segments, "approval-segment");
        assertEquals(LogSegment.StatusEnum.OK, segment.getStatus());
        var postResume = logSegment(segments, "post-resume-output");
        assertEquals(LogSegment.StatusEnum.OK, postResume.getStatus());
        var segmentLog = new String(logApi.getProcessLogSegmentData(process.instanceId(), postResume.getId(), null)
                .readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(segmentLog.contains("v25-m12 post-resume choice=approved"), segmentLog);
        var systemLog = new String(logApi.getProcessLogSegmentData(process.instanceId(), 0L, null)
                .readAllBytes(), StandardCharsets.UTF_8);
        assertFalse(systemLog.contains("v25-m12 post-resume choice=approved"), systemLog);
    }

    @Test
    public void failsFastWhenParallelBranchesUseSameLiveFormNameRemotely() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("duplicate-form"),
                "duplicate-form resource");
        var process = concord.processes().start(new Payload().archive(resource.toURI()));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FAILED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FAILED, result.getStatus(), processLog);
        // assert on parsed log lines: the raw log can interleave JVM warnings mid-line
        assertTrue(processLog.contains("Form 'approval' is used by multiple live parallel branches"),
                processLog);
    }

    @Test
    public void invokesDiscoveredConcordTaskRemotely() throws Exception {
        var process = concord.processes().start(new Payload().concordYml(
                resourceText("concord-task/concord.yml").replace("PROJECT_VERSION", Version.PROJECT_VERSION)));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FINISHED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, result.getStatus(), processLog);
        process.assertLog(".*v25-concord-task children=\\[\\].*");
    }

    @Test
    public void mocksDiscoveredConcordTaskRemotely() throws Exception {
        var process = concord.processes().start(new Payload().concordYml(
                resourceText("mock-task/concord.yml").replace("PROJECT_VERSION", Version.PROJECT_VERSION)));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FINISHED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, result.getStatus(), processLog);
        process.assertLog(".*v25-mock-task result=mocked.*");
        process.assertNoLog(".*Invalid arguments.*");
    }

    @Test
    public void rejectsCorruptCheckpointStateRemotely() throws Exception {
        var process = suspendedCheckpointProcess();
        var checkpoint = uploadTamperedCheckpoint(process, "corrupt-state", state -> {
            assertTrue(state.length > 40, "checkpoint state is unexpectedly short");
            state[40] ^= 1;
        });

        process.restoreCheckpoint(checkpoint.getId());
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FAILED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FAILED, result.getStatus(), processLog);
        assertTrue(processLog.contains("SHA-256"), processLog);
    }

    @Test
    public void rejectsUnsupportedCheckpointFormatRemotely() throws Exception {
        var process = suspendedCheckpointProcess();
        var checkpoint = uploadTamperedCheckpoint(process, "unsupported-format", state ->
                ByteBuffer.wrap(state).putInt(4, 99));

        process.restoreCheckpoint(checkpoint.getId());
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FAILED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FAILED, result.getStatus(), processLog);
        assertTrue(processLog.contains("Unsupported concord-v2.5 state format"), processLog);
    }

    @Test
    public void rejectsOverLimitDurableStateRemotely() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("over-limit-state"), "over-limit-state resource");
        var process = concord.processes().start(new Payload().archive(resource.toURI()));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FAILED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FAILED, result.getStatus(), processLog);
        assertTrue(processLog.contains("maximum depth of 128"), processLog);
    }

    @Test
    public void rejectsEvaluatedReservedCheckpointNameRemotely() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("reserved-checkpoint"),
                "reserved-checkpoint resource");
        var process = concord.processes().start(new Payload().archive(resource.toURI()));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FAILED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FAILED, result.getStatus(), processLog);
        assertTrue(processLog.contains("checkpoint name 'suspend' is reserved"), processLog);
        assertTrue(process.checkpoints().stream().noneMatch(checkpoint -> "suspend".equals(checkpoint.getName())));
    }

    @Test
    public void rejectsUndefinedExpressionAndAllowsGuardedLookupRemotely() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("strict-expression"), "strict-expression resource");
        var failed = concord.processes().start(new Payload().archive(resource.toURI()));
        var failedResult = waitForStatus(failed, ProcessEntry.StatusEnum.FAILED);
        var failedLog = String.join(System.lineSeparator(), failed.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FAILED, failedResult.getStatus(), failedLog);
        assertTrue(failedLog.contains("PropertyNotFoundException"), failedLog);
        assertTrue(failedLog.contains("concord.yml:"), failedLog);

        var guarded = concord.processes().start(new Payload().archive(resource.toURI()).entryPoint("guarded"));
        var guardedResult = waitForStatus(guarded, ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, guardedResult.getStatus(),
                String.join(System.lineSeparator(), guarded.getLogLines()));
        guarded.assertLog(".*v25-strict-expression guarded=false.*");
    }

    @Test
    public void enforcesDryRunTaskReadinessAndRunsMocksRemotely() throws Exception {
        var rejected = concord.processes().start(new Payload().concordYml(
                resourceText("dry-run-rejected/concord.yml").replace("PROJECT_VERSION", Version.PROJECT_VERSION))
                .parameter("dryRun", true));
        var rejectedResult = waitForStatus(rejected, ProcessEntry.StatusEnum.FAILED);
        var rejectedLog = String.join(System.lineSeparator(), rejected.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FAILED, rejectedResult.getStatus(), rejectedLog);
        assertTrue(rejectedLog.contains("Dry-run mode is not supported for 'serializationTest' task"), rejectedLog);

        var mocked = concord.processes().start(new Payload().concordYml(
                resourceText("dry-run/concord.yml").replace("PROJECT_VERSION", Version.PROJECT_VERSION))
                .parameter("dryRun", true));
        var mockedResult = waitForStatus(mocked, ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, mockedResult.getStatus(),
                String.join(System.lineSeparator(), mocked.getLogLines()));
        mocked.assertLog(".*v25-dry-run result=mocked-in-dry-run.*");
    }

    @Test
    public void rejectsSuspensionFromNestedFlowRemotely() throws Exception {
        var process = concord.processes().start(new Payload().concordYml(
                resourceText("nested-suspend/concord.yml").replace("PROJECT_VERSION", Version.PROJECT_VERSION)));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FAILED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FAILED, result.getStatus(), processLog);
        assertTrue(processLog.contains("Nested flow 'suspendedChild' cannot suspend"), processLog);
        assertFalse(process.getEntry().getStatus() == ProcessEntry.StatusEnum.SUSPENDED, processLog);
    }

    @Test
    public void restoresDependencyBeanAcrossFreshRunnerExecutionRemotely() throws Exception {
        var process = concord.processes().start(new Payload().concordYml(
                resourceText("dependency-serialization/concord.yml").replace("PROJECT_VERSION", Version.PROJECT_VERSION)));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, result.getStatus(),
                String.join(System.lineSeparator(), process.getLogLines()));

        var checkpoint = process.checkpoints().stream()
                .filter(item -> "bean-state".equals(item.getName()))
                .findFirst()
                .orElseThrow();
        process.restoreCheckpoint(checkpoint.getId());
        result = waitForStatus(process, ProcessEntry.StatusEnum.FINISHED);
        var processLog = String.join(System.lineSeparator(), process.getLogLines());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, result.getStatus(), processLog);
        assertEquals(2L, process.getLogLines().stream()
                .filter(line -> line.contains("v25-dependency-bean value=bean-survives"))
                .count(), processLog);
    }



    private ConcordProcess suspendedCheckpointProcess() throws Exception {
        var resource = Objects.requireNonNull(getClass().getResource("checkpoint-suspend"),
                "checkpoint-suspend resource");
        var process = concord.processes().start(new Payload().archive(resource.toURI()));
        var result = waitForStatus(process, ProcessEntry.StatusEnum.SUSPENDED);
        assertEquals(ProcessEntry.StatusEnum.SUSPENDED, result.getStatus());
        return process;
    }

    private ProcessCheckpointEntry uploadTamperedCheckpoint(ConcordProcess process, String name,
                                                            Consumer<byte[]> mutator) throws Exception {
        var state = Files.readAllBytes(checkpointStateFile(process));
        mutator.accept(state);
        var archive = Files.createTempFile("runtime-v25-tampered-checkpoint-", ".zip");
        try {
            try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
                output.putNextEntry(new ZipEntry("attachments/state/runtime-v2.5.state"));
                output.write(state);
                output.closeEntry();
            }
            new CheckpointApi(concord.apiClient()).uploadCheckpoint(process.instanceId(), Map.of(
                    "id", UUID.randomUUID(),
                    "correlationId", UUID.randomUUID(),
                    "name", name,
                    "data", Files.readAllBytes(archive)));
        } finally {
            Files.deleteIfExists(archive);
        }
        return process.checkpoints().stream()
                .filter(checkpoint -> name.equals(checkpoint.getName()))
                .findFirst()
                .orElseThrow();
    }

    private static Path checkpointStateFile(ConcordProcess process) throws IOException {
        try (var files = Files.walk(PERSISTENT_WORK_DIR)) {
            return files.filter(path -> path.getFileName().toString().equals("runtime-v2.5.state"))
                    .filter(path -> path.toString().contains(process.instanceId().toString()))
                    .max(Comparator.comparing(Path::toString))
                    .orElseThrow(() -> new AssertionError("Missing persisted state for process " + process.instanceId()));
        }
    }

    private static Path persistentWorkDirectory() {
        try {
            return Files.createDirectories(Path.of("target/runtime-v2.5-persistent-work"));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create persistent runtime-v2.5 work directory", e);
        }
    }

    private ProcessEntry waitForStatus(ConcordProcess process, ProcessEntry.StatusEnum expected) throws Exception {
        var api = new ProcessV2Api(concord.apiClient());
        var deadline = System.nanoTime() + STATUS_TIMEOUT.toNanos();
        ProcessEntry last = null;
        while (System.nanoTime() < deadline) {
            last = api.getProcess(process.instanceId(), Set.of());
            if (last.getStatus() == expected || isTerminal(last.getStatus())) {
                return last;
            }
            sleepBeforeNextStatusPoll();
        }
        throw new AssertionError("Timed out after " + STATUS_TIMEOUT + " waiting for process " + process.instanceId()
                + " to reach " + expected + "; last status: " + (last == null ? "unavailable" : last.getStatus()));
    }

    private ProcessEntry waitForChildStatus(ConcordProcess process, ProcessEntry.StatusEnum expected) throws Exception {
        var api = new ProcessApi(concord.apiClient());
        var deadline = System.nanoTime() + STATUS_TIMEOUT.toNanos();
        ProcessEntry last = null;
        while (System.nanoTime() < deadline) {
            for (var child : api.listSubprocesses(process.instanceId(), null)) {
                last = child;
                if (child.getStatus() == expected || isTerminal(child.getStatus())) {
                    return child;
                }
            }
            sleepBeforeNextStatusPoll();
        }
        throw new AssertionError("Timed out after " + STATUS_TIMEOUT + " waiting for a child of process "
                + process.instanceId() + " to reach " + expected + "; last status: "
                + (last == null ? "no child process" : last.getStatus()));
    }

    private static boolean isTerminal(ProcessEntry.StatusEnum status) {
        return status == ProcessEntry.StatusEnum.FINISHED || status == ProcessEntry.StatusEnum.FAILED
                || status == ProcessEntry.StatusEnum.CANCELLED;
    }

    private static void sleepBeforeNextStatusPoll() throws InterruptedException {
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private String resourceText(String name) throws IOException {
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(name), name + " resource")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static LogSegment logSegment(List<LogSegment> segments, String name) {
        return segments.stream()
                .filter(segment -> name.equals(segment.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing log segment '" + name + "': " + segments));
    }
}
