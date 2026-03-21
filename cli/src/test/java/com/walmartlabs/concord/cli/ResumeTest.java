package com.walmartlabs.concord.cli;

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

import com.walmartlabs.concord.common.PathUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeTest extends AbstractTest {

    @TempDir
    private Path tempDir;

    @Test
    void suspendedRunPersistsStateAndPrintsGuidance() throws Exception {
        var source = preparePayload("suspend");
        var targetDir = CliPaths.defaultTargetDir(source);

        var exitCode = executeIn(source, runArgs());

        assertEquals(CliExitCodes.SUSPENDED, exitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertLog(".*before suspend.*");
        assertOutContainsRegex("Process suspended\\.\\R\\RResume dir: " + quoted(source) + "\\RCommands below assume you are in that directory\\.\\R\\RAdditional waiting events:\\R  ev1\\R\\RContinue with:\\R  Resume event:\\R    concord resume --event ev1");
        assertFalse(stdOut().contains("...done!"), stdOut());
        assertTrue(Files.exists(targetDir.resolve("_attachments").resolve("_state").resolve("instance")));
        assertTrue(Files.exists(targetDir.resolve("_attachments").resolve("_state").resolve("_suspend")));
        assertTrue(Files.exists(targetDir.resolve("_attachments").resolve("_state").resolve("_cliResume.json")));
    }

    @Test
    void resumeConsumesInputFileAndSaveAs() throws Exception {
        var source = preparePayload("suspend");
        var targetDir = CliPaths.defaultTargetDir(source);
        var inputFile = tempDir.resolve("payload.yml");

        Files.writeString(inputFile, "value: resumed-value\n");

        var runExitCode = executeIn(source, runArgs());
        assertEquals(CliExitCodes.SUSPENDED, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var afterRun = stdOut();

        var resumeExitCode = executeIn(source, List.of("resume", "--input-file", inputFile.toString(), "--save-as", "myForm"));

        assertEquals(CliExitCodes.SUCCESS, resumeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var resumeOutput = stdOut().substring(afterRun.length());
        assertTrue(resumeOutput.contains("after resume: resumed-value"), resumeOutput);
        assertTrue(resumeOutput.contains("...done!"), resumeOutput);
        assertFalse(Files.exists(targetDir.resolve("_attachments").resolve("_state")), targetDir.toString());
    }

    @Test
    void resumeConsumesInlineNestedInput() throws Exception {
        var source = preparePayload("suspend");
        var targetDir = CliPaths.defaultTargetDir(source);

        var runExitCode = executeIn(source, runArgs());
        assertEquals(CliExitCodes.SUSPENDED, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var afterRun = stdOut();

        var resumeExitCode = executeIn(source, List.of("resume", "-e", "myForm.value=resumed-inline"));

        assertEquals(CliExitCodes.SUCCESS, resumeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var resumeOutput = stdOut().substring(afterRun.length());
        assertTrue(resumeOutput.contains("after resume: resumed-inline"), resumeOutput);
        assertTrue(resumeOutput.contains("...done!"), resumeOutput);
        assertFalse(Files.exists(targetDir.resolve("_attachments").resolve("_state")), targetDir.toString());
    }

    @Test
    void resumePromptsForPendingStandardForms() throws Exception {
        var source = preparePayload("form");
        var targetDir = CliPaths.defaultTargetDir(source);

        var runExitCode = executeIn(source, runArgs());
        assertEquals(CliExitCodes.SUSPENDED, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertOutContainsRegex("Process suspended\\.\\R\\RResume dir: " + quoted(source) + "\\RCommands below assume you are in that directory\\.\\R\\RPending forms:\\R  Form key\\s+Event ID\\R  myForm\\s+[^\\n]+\\R\\RContinue with:\\R  Describe input:\\R    concord resume --event [^\\s]+ --describe-input\\R  Submit input:\\R    concord resume --event [^\\s]+ --input-file myForm\\.yml");
        assertFalse(stdOut().contains("concord resume " + source), stdOut());
        assertFalse(stdOut().contains("Fill pending form now?"), stdOut());
        assertFalse(stdOut().contains("Fill interactively:"), stdOut());
        assertTrue(Files.exists(targetDir.resolve("_attachments").resolve("_state").resolve("V2forms").resolve("myForm")));

        var afterRun = stdOut();

        var resumeExitCode = withInput("John Smith\n33\n", () -> executeIn(source, List.of("resume")));

        assertEquals(CliExitCodes.SUCCESS, resumeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var resumeOutput = stdOut().substring(afterRun.length());
        assertOutContainsRegex("Pending form input:\\R  myForm -> [^\\n]+");
        assertTrue(resumeOutput.contains("after form: John Smith, 33"), resumeOutput);
        assertTrue(resumeOutput.contains("...done!"), resumeOutput);
        assertFalse(Files.exists(targetDir.resolve("_attachments").resolve("_state")), targetDir.toString());
    }

    @Test
    void resumeFailsFastForFormsWithoutInteractiveInput() throws Exception {
        var source = preparePayload("form");

        var runExitCode = executeIn(source, runArgs());
        assertEquals(CliExitCodes.SUSPENDED, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var resumeExitCode = executeIn(source, List.of("resume"));

        assertEquals(CliExitCodes.INPUT_REQUIRED, resumeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertErrContainsRegex("Resume dir: " + quoted(source) + "\\RCommands below assume you are in that directory\\.\\R\\RPending form requires input in non-interactive mode\\.\\R\\RPending forms:\\R  Form key\\s+Event ID\\R  myForm\\s+[^\\n]+\\R\\RContinue with:\\R  Describe input:\\R    concord resume --event [^\\s]+ --describe-input\\R  Submit input:\\R    concord resume --event [^\\s]+ --input-file myForm\\.yml");
        assertFalse(stdErr().contains("Fill interactively:"), stdErr());
    }

    @Test
    void describeInputShowsExpectedFormShape() throws Exception {
        var source = preparePayload("form");

        var runExitCode = executeIn(source, runArgs());
        assertEquals(CliExitCodes.SUSPENDED, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var afterRun = stdOut();
        var describeExitCode = executeIn(source, List.of("resume", "--describe-input"));

        assertEquals(CliExitCodes.SUCCESS, describeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var describeOutput = stdOut().substring(afterRun.length());
        assertOutContainsRegex("Resume dir: " + quoted(source) + "\\RPending form input:\\R  myForm -> [^\\n]+\\RRequired fields:\\R  name\\R  age");
        assertTrue(describeOutput.contains("Example input file:"), describeOutput);
        assertTrue(describeOutput.contains("myForm:"), describeOutput);
        assertTrue(describeOutput.contains("name: \"\""), describeOutput);
        assertTrue(describeOutput.contains("age: 0"), describeOutput);
    }

    @Test
    void runOffersImmediateFormFill() throws Exception {
        var source = preparePayload("form");
        var targetDir = CliPaths.defaultTargetDir(source);

        var exitCode = withInput("\nJohn Smith\n33\n", () -> executeIn(source, runArgs()));

        assertEquals(CliExitCodes.SUCCESS, exitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertTrue(stdOut().contains("Fill pending form now? (Y/n)"), stdOut());
        assertTrue(stdOut().contains("Name [string, required]:"), stdOut());
        assertTrue(stdOut().contains("after form: John Smith, 33"), stdOut());
        assertTrue(stdOut().contains("...done!"), stdOut());
        assertFalse(stdOut().contains("Process suspended."), stdOut());
        assertFalse(Files.exists(targetDir.resolve("_attachments").resolve("_state")), targetDir.toString());
    }

    @Test
    void runNoPromptSkipsImmediateFormFill() throws Exception {
        var source = preparePayload("form");

        var exitCode = withInput("\nJohn Smith\n33\n", () -> executeIn(source, List.of("run", "--no-default-cfg", "--no-prompt")));

        assertEquals(CliExitCodes.SUSPENDED, exitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertFalse(stdOut().contains("Fill pending form now?"), stdOut());
        assertOutContainsRegex("Process suspended\\.\\R\\RResume dir: " + quoted(source) + "\\RCommands below assume you are in that directory\\.\\R\\RPending forms:\\R  Form key\\s+Event ID\\R  myForm\\s+[^\\n]+");
    }

    @Test
    void interruptedImmediateFormFillCanBeResumed() throws Exception {
        var source = preparePayload("parallelForms");
        var targetDir = CliPaths.defaultTargetDir(source);

        var runExitCode = withInput("\nfirst-value\n", () -> executeIn(source, runArgs()));

        assertEquals(CliExitCodes.ERROR, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertTrue(Files.exists(targetDir.resolve("_attachments").resolve("_state").resolve("_cliResume.json")));

        var afterRun = stdOut();
        var resumeExitCode = withInput("second-value\n", () -> executeIn(source, List.of("resume")));

        assertEquals(CliExitCodes.SUCCESS, resumeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var resumeOutput = stdOut().substring(afterRun.length());
        assertOutContainsRegex("Pending form input:\\R  form2 -> [^\\n]+");
        assertTrue(stdOut().contains("parallel forms: form1=first-value"), stdOut());
        assertTrue(resumeOutput.contains("parallel forms: done one=first-value two=second-value"), resumeOutput);
        assertTrue(resumeOutput.contains("...done!"), resumeOutput);
        assertFalse(Files.exists(targetDir.resolve("_attachments").resolve("_state")), targetDir.toString());
    }

    @Test
    void parallelFormsRunShowsMappingsAndAutomationHints() throws Exception {
        var source = preparePayload("parallelForms");

        var runExitCode = executeIn(source, runArgs());

        assertEquals(CliExitCodes.SUSPENDED, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertOutContainsRegex("Process suspended\\.\\R\\RResume dir: " + quoted(source) + "\\RCommands below assume you are in that directory\\.\\R\\RPending forms:\\R  Form key\\s+Event ID\\R  form1\\s+[^\\n]+\\R  form2\\s+[^\\n]+\\R\\RContinue with:\\R  Describe input:\\R    concord resume --event [^\\s]+ --describe-input\\R    concord resume --event [^\\s]+ --describe-input\\R  Submit input:\\R    concord resume --event [^\\s]+ --input-file form1\\.yml\\R    concord resume --event [^\\s]+ --input-file form2\\.yml");
        assertFalse(stdOut().contains("concord resume " + source), stdOut());
    }

    @Test
    void resumeWithoutPayloadOnMultipleFormsRequiresExplicitEvent() throws Exception {
        var source = preparePayload("parallelForms");

        var runExitCode = executeIn(source, runArgs());
        assertEquals(CliExitCodes.SUSPENDED, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var resumeExitCode = executeIn(source, List.of("resume"));

        assertEquals(CliExitCodes.INPUT_REQUIRED, resumeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertErrContainsRegex("Resume dir: " + quoted(source) + "\\RCommands below assume you are in that directory\\.\\R\\RPending forms require input or explicit event selection\\.\\R\\RPending forms:\\R  Form key\\s+Event ID\\R  form1\\s+[^\\n]+\\R  form2\\s+[^\\n]+");
        assertTrue(stdErr().contains("--describe-input"), stdErr());
        assertTrue(stdErr().contains("--input-file form1.yml"), stdErr());
        assertTrue(stdErr().contains("--input-file form2.yml"), stdErr());
    }

    @Test
    void describeInputRequiresEventForMultipleForms() throws Exception {
        var source = preparePayload("parallelForms");

        var runExitCode = executeIn(source, runArgs());
        assertEquals(CliExitCodes.SUSPENDED, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var describeExitCode = executeIn(source, List.of("resume", "--describe-input"));

        assertEquals(CliExitCodes.INPUT_REQUIRED, describeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertErrContainsRegex("Resume dir: " + quoted(source) + "\\RCommands below assume you are in that directory\\.\\R\\RPending forms require explicit event selection before describing input\\.\\R\\RPending forms:\\R  Form key\\s+Event ID\\R  form1\\s+[^\\n]+\\R  form2\\s+[^\\n]+\\R\\RContinue with:\\R  Describe input:");
        assertTrue(stdErr().contains("--describe-input"), stdErr());
    }

    @Test
    void mixedFormAndEventGuidanceIncludesBothChoices() throws Exception {
        var source = preparePayload("mixedFormEvent");

        var runExitCode = executeIn(source, runArgs());

        assertEquals(CliExitCodes.SUSPENDED, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertOutContainsRegex("Process suspended\\.\\R\\RResume dir: " + quoted(source) + "\\RCommands below assume you are in that directory\\.\\R\\RPending forms:\\R  Form key\\s+Event ID\\R  approvalForm\\s+[^\\n]+\\R\\RAdditional waiting events:\\R  ev_timeout\\R\\RContinue with:\\R  Describe input:\\R    concord resume --event [^\\s]+ --describe-input\\R  Submit input:\\R    concord resume --event [^\\s]+ --input-file approvalForm\\.yml\\R  Resume event:\\R    concord resume --event ev_timeout");
    }

    @Test
    void fileUploadFormDescribeInputAndResumeFailure() throws Exception {
        var source = preparePayload("fileForm");
        var inputFile = tempDir.resolve("uploadForm.yml");
        Files.writeString(inputFile, "uploadForm:\n  attachment: path/to/file\n");

        var runExitCode = executeIn(source, runArgs());
        assertEquals(CliExitCodes.SUSPENDED, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertTrue(stdOut().contains("not supported for file-upload fields"), stdOut());

        var afterRun = stdOut();
        var describeExitCode = executeIn(source, List.of("resume", "--describe-input"));

        assertEquals(CliExitCodes.SUCCESS, describeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var describeOutput = stdOut().substring(afterRun.length());
        assertTrue(describeOutput.contains("File-upload fields:"), describeOutput);
        assertTrue(describeOutput.contains("attachment"), describeOutput);
        assertTrue(describeOutput.contains("not supported for file-upload fields"), describeOutput);

        var resumeExitCode = executeIn(source, List.of("resume", "--input-file", inputFile.toString()));

        assertEquals(CliExitCodes.NON_INTERACTIVE_UNSUPPORTED, resumeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertErrContainsRegex("Resume dir: " + quoted(source) + "\\RCommands below assume you are in that directory\\.\\R\\RPending form cannot be submitted non-interactively because it contains file-upload fields\\.\\R\\RPending forms:\\R  Form key\\s+Event ID\\R  uploadForm\\s+[^\\n]+");
    }

    @Test
    void suspendedMetadataOmitsApiKeyAndResumeReloadsStoredContextAndOverrides() throws Exception {
        var source = preparePayload("secretResume");
        var targetDir = CliPaths.defaultTargetDir(source);
        var homeDir = tempDir.resolve("home");
        var overrideSecretDir = tempDir.resolve("override-secrets");
        writeSecret(overrideSecretDir, "Default", "resumeSecret", "value-from-override");
        writeCliConfig(homeDir, """
                contexts:
                  default:
                    secrets:
                      local:
                        enabled: true
                        writable: true
                        dir: "%s"
                      remote:
                        enabled: true
                        writable: false
                        baseUrl: "http://localhost:8001"
                        apiKey: "resume-api-key"
                  another:
                    secrets:
                      local:
                        dir: "%s"
                """.formatted(tempDir.resolve("wrong-default-secrets"), tempDir.resolve("wrong-another-secrets")));

        var runExitCode = withUserHome(homeDir, () -> executeIn(source, runArgs("--context", "another", "--secret-dir", overrideSecretDir.toString())));
        assertEquals(CliExitCodes.SUSPENDED, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var metadata = Files.readString(targetDir.resolve("_attachments").resolve("_state").resolve("_cliResume.json"));
        assertFalse(metadata.contains("resume-api-key"), metadata);
        assertTrue(metadata.contains("\"contextName\" : \"another\""), metadata);
        assertTrue(metadata.contains(overrideSecretDir.toString()), metadata);

        var afterRun = stdOut();
        var resumeExitCode = withUserHome(homeDir, () -> executeIn(source, List.of("resume")));

        assertEquals(CliExitCodes.SUCCESS, resumeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        var resumeOutput = stdOut().substring(afterRun.length());
        assertTrue(resumeOutput.contains("after resume secret: value-from-override"), resumeOutput);
    }

    @Test
    void passwordRetryPromptsDoNotEchoSecretsAndRunCleanupRemovesSessionFiles() throws Exception {
        var source = preparePayload("passwordRetry");
        var targetDir = CliPaths.defaultTargetDir(source);

        var exitCode = withInput("\nentered-secret\n10\nentered-secret\n33\n", () -> executeIn(source, runArgs()));

        assertEquals(CliExitCodes.SUCCESS, exitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertTrue(stdOut().contains("Fill pending form now? (Y/n)"), stdOut());
        assertTrue(stdOut().contains("after password form: 33"), stdOut());
        assertEquals(2, countMatches(stdOut(), "Password \\[string, required, default: <hidden>\\]:"), stdOut());
        assertFalse(stdOut().contains("seed-secret"), stdOut());
        assertFalse(stdOut().contains("entered-secret"), stdOut());
        assertFalse(stdErr().contains("seed-secret"), stdErr());
        assertFalse(stdErr().contains("entered-secret"), stdErr());
        assertFalse(Files.exists(targetDir.resolve("_attachments").resolve("_session_files")), targetDir.toString());
    }

    @Test
    void resumeCleanupRemovesSessionFiles() throws Exception {
        var source = preparePayload("passwordSuspend");
        var targetDir = CliPaths.defaultTargetDir(source);

        var runExitCode = withInput("\nresume-secret\n33\n", () -> executeIn(source, runArgs()));
        assertEquals(CliExitCodes.SUSPENDED, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        var sessionFilesDir = targetDir.resolve("_attachments").resolve("_session_files");
        Files.createDirectories(sessionFilesDir);
        Files.writeString(sessionFilesDir.resolve("sensitive.json"), "test");

        var afterRun = stdOut();
        var resumeExitCode = executeIn(source, List.of("resume"));

        assertEquals(CliExitCodes.SUCCESS, resumeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        var resumeOutput = stdOut().substring(afterRun.length());
        assertTrue(resumeOutput.contains("after resume: 33"), resumeOutput);
        assertFalse(Files.exists(targetDir.resolve("_attachments").resolve("_state")), targetDir.toString());
        assertFalse(Files.exists(sessionFilesDir), targetDir.toString());
    }

    @Test
    void cleanupRemovesStateAndSessionFiles() throws Exception {
        var workDir = tempDir.resolve("cleanup");
        var stateDir = workDir.resolve("_attachments").resolve("_state");
        var sessionFilesDir = workDir.resolve("_attachments").resolve("_session_files");
        Files.createDirectories(stateDir);
        Files.createDirectories(sessionFilesDir);
        Files.writeString(stateDir.resolve("instance"), "state");
        Files.writeString(sessionFilesDir.resolve("sensitive.json"), "session");

        LocalSuspendPersistence.cleanup(workDir);

        assertFalse(Files.exists(stateDir), workDir.toString());
        assertFalse(Files.exists(sessionFilesDir), workDir.toString());
    }

    private int execute(List<String> args) {
        var app = new App();
        var cmd = new CommandLine(app);
        return cmd.execute(args.toArray(new String[0]));
    }

    private int executeIn(Path userDir, List<String> args) {
        var originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", userDir.toString());
        try {
            return execute(args);
        } finally {
            if (originalUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", originalUserDir);
            }
        }
    }

    private static List<String> runArgs(String... extraArgs) {
        var result = new java.util.ArrayList<String>();
        result.add("run");
        result.add("--no-default-cfg");
        result.addAll(List.of(extraArgs));
        return result;
    }

    private Path preparePayload(String payload) throws Exception {
        var uri = ResumeTest.class.getResource(payload).toURI();
        var source = Paths.get(uri);
        PathUtils.copy(source, tempDir);
        return tempDir;
    }

    private static String quoted(Path path) {
        return Pattern.quote(path.toString());
    }

    private <T> T withUserHome(Path userHome, java.util.concurrent.Callable<T> action) throws Exception {
        var originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", userHome.toString());
        try {
            return action.call();
        } finally {
            if (originalUserHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", originalUserHome);
            }
        }
    }

    private static void writeCliConfig(Path homeDir, String contents) throws IOException {
        var configDir = homeDir.resolve(".concord");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("cli.yaml"), contents);
    }

    private static void writeSecret(Path secretDir, String orgName, String secretName, String value) throws IOException {
        var dir = secretDir.resolve(orgName);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(secretName), value);
    }

    private static int countMatches(String value, String regex) {
        var matcher = Pattern.compile(regex).matcher(value);
        var count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
