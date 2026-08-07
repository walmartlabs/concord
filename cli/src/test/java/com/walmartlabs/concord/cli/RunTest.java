package com.walmartlabs.concord.cli;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.common.PathUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunTest extends AbstractTest {

    @TempDir
    private Path tempDir;

    @Test
    void runTest() throws Exception {
        Map<String, Object> extraVars = Collections.singletonMap("name", "Concord");
        List<String> args = new ArrayList<>();
        for (Map.Entry<String, Object> e : extraVars.entrySet()) {
            args.add("-e");
            args.add(e.getKey() + "=" + e.getValue());
        }

        int exitCode = run("simple", args);
        assertExitCode(0, exitCode);
        assertLog(".*Hello, Concord.*");
        assertEquals(0, exitCode);
        // default dependencies should be added
        assertLog(".*concord-tasks-" + Version.getVersion() + ".jar.*");
        assertLog(".*http-tasks-" + Version.getVersion() + ".jar.*");
        assertLog(".*slack-tasks-" + Version.getVersion() + ".jar.*");
    }

    @Test
    void testResourceTask() throws Exception {
        int exitCode = run("resourceTask", Collections.emptyList());
        assertExitCode(0, exitCode);
        assertLog(".*\"k\" : \"v\".*");
    }

    @Test
    void testDepsFromProfile() throws Exception {
        int exitCode = run("profileDeps", Arrays.asList("-p", "test"));
        assertExitCode(0, exitCode);
        assertLog(".*exists=true.*");
    }

    @Test
    void runtimeV25AggregatesExtraDependenciesFromAllActiveProfiles() throws Exception {
        var exitCode = run("v25ProfileDeps", List.of("--no-default-cfg", "-p", "first", "-p", "second"));

        assertExitCode(CliExitCodes.SUCCESS, exitCode);
        assertLog(".*file=true.*");
        assertLog(".*http=true.*");
    }

    @Test
    void testCliCheckpointService() throws Exception {
        int exitCode = run("cliCheckpointService", Collections.emptyList());
        assertExitCode(0, exitCode);
        assertLog(".*Checkpoint.*ignored.*", 2);
    }

    @Test
    void testCustomDefaultConfig() throws Exception {
        int exitCode = run("defaultCfg", Collections.emptyList(), "defaults.yml");
        assertExitCode(0, exitCode);
        assertLog(".*file-tasks-" + Version.getVersion() + ".jar.*");
    }

    @Test
    void testCustomDefaultTaskVars() throws Exception {
        int exitCode = run("defaultTaskVars", List.of("--default-task-vars", tempDir.resolve("defaultTaskVars.json").toString()));
        assertExitCode(0, exitCode);
        assertLog(".*Unknown action: 'customInvalidAction'. Available actions.*");
    }

    @Test
    void testProcessProjectInfo() throws Exception {
        Map<String, Object> extraVars = new HashMap<>();
        extraVars.put("processInfo.sessionToken", "test-token");
        extraVars.put("projectInfo.orgName", "test-org");

        List<String> args = new ArrayList<>();
        for (Map.Entry<String, Object> e : extraVars.entrySet()) {
            args.add("-e");
            args.add(e.getKey() + "=" + e.getValue());
        }

        int exitCode = run("processProjectInfo", args);
        assertExitCode(0, exitCode);
        assertLog(".*processInfo: \\{sessionToken=test-token}.*");
        assertLog(".*projectInfo: \\{orgName=test-org}.*");
    }

    @Test
    void testParallelErrorCallStack() throws Exception {
        int exitCode = run("parallelErrorCallStack", Collections.emptyList());
        assertExitCode(-1, exitCode);
        assertOutContainsRegex("(?s).*Parallel execution errors:.*\\[\\d+\\].*Error: boom.*Call stack:.*flow: innerFlow.*flow: flowA.*");
        assertOutContainsRegex("(?s).*Parallel execution errors:.*\\[\\d+\\].*Error: boom.*Call stack:.*flow: innerFlow.*flow: flowB.*");
    }

    @Test
    void runtimeV25FailureReturnsProcessFailureExitCode() throws Exception {
        var exitCode = run("v25Failure", List.of("--no-default-cfg"));
        assertExitCode(CliExitCodes.PROCESS_FAILED, exitCode);
        assertErrContainsRegex("(?s).*V25_STEP_FAILED: expected-v2.5-failure.*concord.yml:5.*");
    }

    @Test
    void runtimeV25PlanFailuresUseDiagnosticsAndProcessFailureExitCode() throws Exception {
        var exitCode = run("lintV25UnknownCall", List.of("--no-default-cfg"));

        assertExitCode(CliExitCodes.PROCESS_FAILED, exitCode);
        assertErrContainsRegex("ERROR V25_PLAN at concord\\.yml:\\d+:\\d+-\\d+:\\d+ "
                + "\\(flows\\.default\\[0\\]\\): Unknown flow 'missingFlow'");
    }

    @Test
    void runtimeV25MasksSensitiveFailures() throws Exception {
        var exitCode = run("v25SensitiveFailure", List.of("--no-default-cfg"));

        assertExitCode(CliExitCodes.PROCESS_FAILED, exitCode);
        assertFalse(stdErr().contains("v25-secret-value"), stdErr());
    }

    @Test
    void runtimeV25ForwardsDependencyElFunctions() throws Exception {
        var exitCode = run("v25ElFunction", Collections.emptyList());

        assertExitCode(CliExitCodes.SUCCESS, exitCode);
        assertLog(".*v25-el-function-world.*");
    }

    @Test
    void runtimeV25LogsStepsAtVerboseAndTaskParametersAtDoubleVerbose() throws Exception {
        var stepExitCode = run("v25Success", List.of("-v"));

        assertExitCode(CliExitCodes.SUCCESS, stepExitCode);
        assertOutContainsRegex("(?s).*>>> '.*' @ concord\\.yml:\\d+.*");

        var second = Files.createDirectories(tempDir.resolve("second"));
        PathUtils.copy(Paths.get(RunTest.class.getResource("v25Success").toURI()), second);
        var taskExitCode = run(second, List.of("-vv"), null);

        assertExitCode(CliExitCodes.SUCCESS, taskExitCode);
        assertOutContainsRegex("(?s).*     in:.*     out:.*     duration: .*ms.*");
    }

    @Test
    void runtimeV25ExecutesReorderedTaskExpressionScriptAndFlowCall() throws Exception {
        var exitCode = run("v25Success", Collections.emptyList());
        assertExitCode(CliExitCodes.SUCCESS, exitCode);
        assertLog(".*cli-v2.5-ok answer=42 script=43 nested=43.*");
    }

    @Test
    void runtimeV25AppliesConfiguredActiveProfilesBeforeLaunching() throws Exception {
        var exitCode = run("v25ConfiguredProfile", Collections.emptyList());
        assertExitCode(CliExitCodes.SUCCESS, exitCode);
        assertLog(".*v25-configured-profile \\[configured\\].*");
    }

    @Test
    void runtimeV25UsesCliDefaultTaskVariables() throws Exception {
        var exitCode = run("v25DefaultTaskVars",
                List.of("--default-task-vars", tempDir.resolve("v25DefaultTaskVars.json").toString()));
        assertExitCode(CliExitCodes.SUCCESS, exitCode);
        assertLog(".*v25-default-task-vars.*");
    }

    @Test
    void runtimeV25SuspendsAndResumes() throws Exception {
        var suspended = run("v25Suspend", List.of("--no-default-cfg"));
        assertExitCode(CliExitCodes.SUSPENDED, suspended);
        assertOutContainsRegex("(?s).*Continue with:.*concord resume --event continue.*");

        var resumed = new CommandLine(new App()).execute("resume", "--event", "continue",
                tempDir.resolve("target").toString());
        assertExitCode(CliExitCodes.SUCCESS, resumed);
    }


    @Test
    void runtimeV25SuspensionGuidanceIncludesAllWaitingEventsAndPendingForms() throws Exception {
        var multiEventExitCode = run("v25MultiSuspend", List.of("--no-default-cfg", "--no-prompt"));

        assertExitCode(CliExitCodes.SUSPENDED, multiEventExitCode);
        assertOutContainsRegex("(?s).*concord resume --event first-event.*concord resume --event second-event.*");


        var resumed = new CommandLine(new App()).execute("resume", "--event", "first-event",
                tempDir.resolve("target").toString());

        assertExitCode(CliExitCodes.SUSPENDED, resumed);
        assertOutContainsRegex("(?s).*concord resume --event second-event.*");
        var formDir = Files.createDirectories(tempDir.resolve("formSuspend"));
        PathUtils.copy(Paths.get(RunTest.class.getResource("v25FormSuspend").toURI()), formDir);
        var formExitCode = run(formDir, List.of("--no-default-cfg", "--no-prompt"), null);

        assertExitCode(CliExitCodes.SUSPENDED, formExitCode);
        assertOutContainsRegex("(?s).*Pending forms:.*approval.*Describe input:.*Submit input:.*");
    }
    @Test
    void runtimeV25ResumePassesManualInput() throws Exception {
        var suspended = run("v25ResumeInput", List.of("--no-default-cfg"));
        assertExitCode(CliExitCodes.SUSPENDED, suspended);

        var resumed = new CommandLine(new App()).execute("resume", "--event", "continue", "-e", "answer=42",
                tempDir.resolve("target").toString());
        assertExitCode(CliExitCodes.SUCCESS, resumed);
        assertLog(".*v25-resume-answer=42.*");
    }

    @Test
    void runtimeV25ResumePreservesValuesChangedBeforeSuspension() throws Exception {
        var suspended = run("v25ResumePreservesValues", List.of("-e", "x=1"));
        assertExitCode(CliExitCodes.SUSPENDED, suspended);

        var resumed = new CommandLine(new App()).execute("resume", "--event", "continue",
                tempDir.resolve("target").toString());
        assertExitCode(CliExitCodes.SUCCESS, resumed);
        assertLog(".*v25-resume-x=2.*");
    }

    @Test
    void runtimeV25ExposesConfiguredProcessMetadataToTasks() throws Exception {
        var exitCode = run("v25ProcessConfiguration", List.of());

        assertExitCode(CliExitCodes.SUCCESS, exitCode);
        assertLog(".*v25-process-configuration meta=local events=false out=processResult.*");
    }

    @Test
    void runAndResumeLocalSuspension() throws Exception {
        var suspended = run("suspend", List.of("--no-prompt"));
        assertExitCode(CliExitCodes.SUSPENDED, suspended);

        var resumed = new CommandLine(new App()).execute("resume", "--event", "ev1",
                "-e", "myForm.value=answer", tempDir.resolve("target").toString());
        assertExitCode(CliExitCodes.SUCCESS, resumed);
        assertLog(".*after resume: answer.*");
    }

    @Test
    void importFailuresDoNotRenderSecretPasswords() throws Exception {
        assertImportFailureDoesNotRenderPassword("importSecretFailure", Collections.emptyList());
        PathUtils.deleteRecursively(tempDir);
        java.nio.file.Files.createDirectories(tempDir);
        assertImportFailureDoesNotRenderPassword("v25ImportSecretFailure", List.of("--no-default-cfg"));
    }

    @Test
    void runtimeV25MatchesV2ForSequentialCompatibilityCorpus() throws Exception {
        var fixtures = List.of(
                new CompatibilityFixture("""
                        configuration:
                          runtime: __RUNTIME__
                          arguments:
                            base: 40
                        flows:
                          default:
                            - expr: ${base + 2}
                              out: answer
                            - call: child
                              in:
                                value: ${answer}
                              out: nested
                            - task: log
                              in:
                                msg: "compat-basic answer=${answer} nested=${nested}"
                          child:
                            - set:
                                nested: ${value + 1}
                        """, null),
                new CompatibilityFixture("""
                        configuration:
                          runtime: __RUNTIME__
                        flows:
                          default:
                            - block:
                                - expr: ${item * 2}
                                  out: doubled
                              loop:
                                items: [1, 2, 3]
                              out: doubled
                            - try:
                                - throw: handled
                              error:
                                - set:
                                    handled: ${lastError.message}
                              out: handled
                            - task: log
                              in:
                                msg: "compat-control doubled=${doubled} handled=${handled}"
                        """, null),
                new CompatibilityFixture("""
                        configuration:
                          runtime: __RUNTIME__
                        flows:
                          default:
                            - expr: ${[1, 2, 3].stream().map(i -> i * 2).toList()}
                              out: doubled
                            - task: log
                              in:
                                msg: "compat-stream doubled=${doubled}"
                        """, "compat-stream doubled=[2, 4, 6]"),
                new CompatibilityFixture("""
                        configuration:
                          runtime: __RUNTIME__
                          arguments:
                            aVar:
                              x: 1
                        flows:
                          default:
                            - expr: ${aVar.x = aVar.x + 1}
                            - task: log
                              in:
                                msg: "compat-nested-assignment x=${aVar.x}"
                        """, "compat-nested-assignment x=2"),
                new CompatibilityFixture("""
                        configuration:
                          runtime: __RUNTIME__
                          arguments:
                            a:
                              out1: evaluated
                              existing: keep
                            x:
                              a:
                                out1: ${a.out1}
                                newValue: ${a.out1}
                        flows:
                          default:
                            - expr: ${evalAsMap(x)}
                              out: evaluated
                            - task: log
                              in:
                                msg: "compat-eval-as-map out1=${evaluated.a.out1} existing=${evaluated.a.existing} new=${evaluated.a.newValue}"
                        """, "compat-eval-as-map out1=evaluated existing=keep new=evaluated"),
                new CompatibilityFixture("""
                        configuration:
                          runtime: __RUNTIME__
                        flows:
                          default:
                            - set:
                                obj.name: Concord
                                obj.msg: "Hello, ${obj.name}!"
                            - task: log
                              in:
                                msg: "compat-set-siblings msg=${obj.msg}"
                        """, "compat-set-siblings msg=Hello, Concord!"),
                new CompatibilityFixture("""
                        configuration:
                          runtime: __RUNTIME__
                        flows:
                          default:
                            - script: js
                              in:
                                config:
                                  host: host
                                  timeout: 1
                              retry:
                                times: 1
                                delay: 0
                                in:
                                  config:
                                    timeout: 2
                              body: |
                                if (context.variables().get("__retry_attemptNo") == 0) {
                                  throw new Error("retry");
                                }
                                result.set("config", config);
                              out: scriptResult
                            - task: log
                              in:
                                msg: "compat-retry-deep-merge host=${scriptResult.config.host} timeout=${scriptResult.config.timeout}"
                        """, "compat-retry-deep-merge host=host timeout=2"));

        for (var i = 0; i < fixtures.size(); i++) {
            var fixture = fixtures.get(i);
            var v2 = runCompatibilityFixture(i, fixture.source(), "concord-v2");
            var v25 = runCompatibilityFixture(i, fixture.source(), "concord-v2.5");

            assertEquals(CliExitCodes.SUCCESS, v2.exitCode(), v2.output());
            assertEquals(v2.exitCode(), v25.exitCode(), v25.output());
            assertFalse("<missing compatibility marker>".equals(v2.marker()), "fixture " + i);
            if (fixture.expectedOutput() != null) {
                assertTrue(v2.output().contains(fixture.expectedOutput()), v2.output());
            }
            assertEquals(v2.marker(), v25.marker(), "fixture " + i);
        }
    }


    private void assertExitCode(int expected, int current) {
        assertEquals(expected, current, () -> "out:\n" + stdOut() + "\n\n" + "err:\n" + stdErr());
    }

    private int run(String payload, List<String> args) throws Exception {
        return run(payload, args, null);
    }

    private int run(String payload, List<String> args, String defaultCfg) throws Exception {
        URI uri = RunTest.class.getResource(payload).toURI();
        Path source = Paths.get(uri);

        PathUtils.copy(source, tempDir);
        return run(tempDir, args, defaultCfg);
    }

    private int run(Path workDir, List<String> args, String defaultCfg) {
        App app = new App();
        CommandLine cmd = new CommandLine(app);

        List<String> effectiveArgs = new ArrayList<>();
        effectiveArgs.add("run");
        effectiveArgs.addAll(args);
        effectiveArgs.add(workDir.toString());

        if (defaultCfg != null) {
            effectiveArgs.add("--default-cfg");
            effectiveArgs.add(workDir.resolve(defaultCfg).toString());
        }

        return cmd.execute(effectiveArgs.toArray(new String[0]));
    }

    private CompatibilityResult runCompatibilityFixture(int index, String source, String runtime) throws Exception {
        var workDir = tempDir.resolve("compat-" + index + "-" + runtime);
        java.nio.file.Files.createDirectories(workDir);
        java.nio.file.Files.writeString(workDir.resolve("concord.yml"), source.replace("__RUNTIME__", runtime));
        var outputOffset = stdOut().length();
        var errorOffset = stdErr().length();
        var exitCode = run(workDir, List.of(), null);
        var output = stdOut().substring(outputOffset);
        var error = stdErr().substring(errorOffset);
        var marker = output.lines()
                .filter(line -> line.contains("compat-"))
                .map(line -> line.substring(line.indexOf("compat-")))
                .findFirst()
                .orElse("<missing compatibility marker>");
        return new CompatibilityResult(exitCode, marker, output + "\n" + error);
    }

    private record CompatibilityFixture(String source, String expectedOutput) {
    }

    private record CompatibilityResult(int exitCode, String marker, String output) {
    }

    private void assertImportFailureDoesNotRenderPassword(String payload, List<String> args) throws Exception {
        var exitCode = run(payload, args);
        assertExitCode(CliExitCodes.PROCESS_FAILED, exitCode);
        assertErrContainsRegex(".*Error while processing git import from .* to 'imported'.*");
        assertFalse(stdErr().contains("secret-import-password"), () -> "err:\n" + stdErr());
    }
}
