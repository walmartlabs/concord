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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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

        assertEquals(0, exitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertLog(".*before suspend.*");
        assertLog(".*Process suspended\\. Waiting event: ev1.*");
        assertLog(".*Resume with: concord resume --event ev1.*");
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
        assertEquals(0, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var afterRun = stdOut();

        var resumeExitCode = executeIn(source, List.of("resume", "--input-file", inputFile.toString(), "--save-as", "myForm"));

        assertEquals(0, resumeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

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
        assertEquals(0, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var afterRun = stdOut();

        var resumeExitCode = executeIn(source, List.of("resume", "-e", "myForm.value=resumed-inline"));

        assertEquals(0, resumeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

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
        assertEquals(0, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertTrue(stdOut().contains("Process suspended. Pending form: myForm"), stdOut());
        assertTrue(stdOut().contains("Resume with: concord resume"), stdOut());
        assertFalse(stdOut().contains("Fill pending form now?"), stdOut());
        assertFalse(stdOut().contains("--event"), stdOut());
        assertTrue(Files.exists(targetDir.resolve("_attachments").resolve("_state").resolve("V2forms").resolve("myForm")));

        var afterRun = stdOut();

        var resumeExitCode = withInput("John Smith\n33\n", () -> executeIn(source, List.of("resume")));

        assertEquals(0, resumeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var resumeOutput = stdOut().substring(afterRun.length());
        assertTrue(resumeOutput.contains("Process suspended on form: myForm"), resumeOutput);
        assertTrue(resumeOutput.contains("after form: John Smith, 33"), resumeOutput);
        assertTrue(resumeOutput.contains("...done!"), resumeOutput);
        assertFalse(Files.exists(targetDir.resolve("_attachments").resolve("_state")), targetDir.toString());
    }

    @Test
    void resumeFailsFastForFormsWithoutInteractiveInput() throws Exception {
        var source = preparePayload("form");

        var runExitCode = executeIn(source, runArgs());
        assertEquals(0, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var resumeExitCode = executeIn(source, List.of("resume"));

        assertEquals(1, resumeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertTrue(stdErr().contains("Pending form requires interactive input."), stdErr());
        assertTrue(stdErr().contains("--input-file"), stdErr());
    }

    @Test
    void runOffersImmediateFormFill() throws Exception {
        var source = preparePayload("form");
        var targetDir = CliPaths.defaultTargetDir(source);

        var exitCode = withInput("\nJohn Smith\n33\n", () -> executeIn(source, runArgs()));

        assertEquals(0, exitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertTrue(stdOut().contains("Fill pending form now? (Y/n)"), stdOut());
        assertTrue(stdOut().contains("Name [string, required]:"), stdOut());
        assertTrue(stdOut().contains("after form: John Smith, 33"), stdOut());
        assertTrue(stdOut().contains("...done!"), stdOut());
        assertFalse(stdOut().contains("Process suspended. Pending form: myForm"), stdOut());
        assertFalse(stdOut().contains("Resume with: concord resume"), stdOut());
        assertFalse(stdOut().contains("Process suspended on form: myForm"), stdOut());
        assertFalse(Files.exists(targetDir.resolve("_attachments").resolve("_state")), targetDir.toString());
    }

    @Test
    void interruptedImmediateFormFillCanBeResumed() throws Exception {
        var source = preparePayload("parallelForms");
        var targetDir = CliPaths.defaultTargetDir(source);

        var runExitCode = withInput("\nfirst-value\n", () -> executeIn(source, runArgs()));

        assertEquals(1, runExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());
        assertTrue(Files.exists(targetDir.resolve("_attachments").resolve("_state").resolve("_cliResume.json")));

        var afterRun = stdOut();
        var resumeExitCode = withInput("second-value\n", () -> executeIn(source, List.of("resume")));

        assertEquals(0, resumeExitCode, () -> "out:\n" + stdOut() + "\n\nerr:\n" + stdErr());

        var resumeOutput = stdOut().substring(afterRun.length());
        assertTrue(resumeOutput.contains("Process suspended on form: form2"), resumeOutput);
        assertTrue(stdOut().contains("parallel forms: form1=first-value"), stdOut());
        assertTrue(resumeOutput.contains("parallel forms: done one=first-value two=second-value"), resumeOutput);
        assertTrue(resumeOutput.contains("...done!"), resumeOutput);
        assertFalse(Files.exists(targetDir.resolve("_attachments").resolve("_state")), targetDir.toString());
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

    private static List<String> runArgs() {
        return List.of("run", "--no-default-cfg");
    }

    private Path preparePayload(String payload) throws Exception {
        var uri = ResumeTest.class.getResource(payload).toURI();
        var source = Paths.get(uri);
        PathUtils.copy(source, tempDir);
        return tempDir;
    }
}
