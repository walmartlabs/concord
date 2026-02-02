package com.walmartlabs.concord.it.runtime.v2;

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
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.it.common.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.walmartlabs.concord.it.runtime.v2.Utils.resourceToString;

public class TaskSchemaValidationIT extends AbstractTest {

    @RegisterExtension
    public static final ConcordRule concord = ConcordConfiguration.configure();

    /**
     * Test that valid input passes validation.
     */
    @Test
    public void testValidInput() throws Exception {
        String concordYml = resourceToString(TaskSchemaValidationIT.class.getResource("taskSchemaValidation/validInput/concord.yml"))
                .replaceAll("PROJECT_VERSION", Version.PROJECT_VERSION);

        Payload payload = new Payload().concordYml(concordYml);

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*SchemaTestTask: message=hello, count=5.*");
        proc.assertLog(".*Result: hello.*");
    }

    /**
     * Test that invalid input with FAIL mode throws and process fails.
     */
    @Test
    public void testInvalidInputFail() throws Exception {
        String concordYml = resourceToString(TaskSchemaValidationIT.class.getResource("taskSchemaValidation/invalidInputFail/concord.yml"))
                .replaceAll("PROJECT_VERSION", Version.PROJECT_VERSION);

        Payload payload = new Payload().concordYml(concordYml);

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FAILED);

        proc.assertLog(".*Task 'schemaTest' in validation failed.*");
        proc.assertLog(".*message.*");
    }

    /**
     * Test that invalid input with WARN mode logs warning but process completes.
     */
    @Test
    public void testInvalidInputWarn() throws Exception {
        String concordYml = resourceToString(TaskSchemaValidationIT.class.getResource("taskSchemaValidation/invalidInputWarn/concord.yml"))
                .replaceAll("PROJECT_VERSION", Version.PROJECT_VERSION);

        Payload payload = new Payload().concordYml(concordYml);

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*validation errors.*");
        proc.assertLog(".*Result: default.*");
    }

    /**
     * Test that DISABLED mode skips validation entirely.
     */
    @Test
    public void testValidationDisabled() throws Exception {
        String concordYml = resourceToString(TaskSchemaValidationIT.class.getResource("taskSchemaValidation/validationDisabled/concord.yml"))
                .replaceAll("PROJECT_VERSION", Version.PROJECT_VERSION);

        Payload payload = new Payload().concordYml(concordYml);

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*Result: hello.*");
    }

    /**
     * Test that task without schema works normally.
     */
    @Test
    public void testNoSchema() throws Exception {
        Payload payload = new Payload()
                .archive(resource("taskSchemaValidation/noSchema"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*hello from log task.*");
    }

    /**
     * Test that multiple validation errors are collected.
     */
    @Test
    public void testMultipleErrors() throws Exception {
        String concordYml = resourceToString(TaskSchemaValidationIT.class.getResource("taskSchemaValidation/multipleErrors/concord.yml"))
                .replaceAll("PROJECT_VERSION", Version.PROJECT_VERSION);

        Payload payload = new Payload().concordYml(concordYml);

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FAILED);

        proc.assertLog(".*Task 'schemaTest' in validation failed.*");
    }

    /**
     * Test that valid concord task input passes validation.
     */
    @Test
    public void testConcordTaskValidInput() throws Exception {
        Payload payload = new Payload()
                .archive(resource("taskSchemaValidation/concordTaskValid"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*Done.*");
    }

    /**
     * Test that invalid concord task input fails validation.
     */
    @Test
    public void testConcordTaskInvalidInput() throws Exception {
        Payload payload = new Payload()
                .archive(resource("taskSchemaValidation/concordTaskInvalid"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FAILED);

        proc.assertLog(".*Task 'concord' in validation failed.*");
        proc.assertLog(".*action.*");
    }
}
