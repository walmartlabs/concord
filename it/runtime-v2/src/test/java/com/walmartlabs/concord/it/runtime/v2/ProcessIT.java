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
import ca.ibodrov.concord.testcontainers.junit4.ConcordRule;
import com.walmartlabs.concord.client.FormListEntry;
import com.walmartlabs.concord.client.FormSubmitResponse;
import com.walmartlabs.concord.client.ProcessCheckpointEntry;
import com.walmartlabs.concord.client.ProcessEntry;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static com.walmartlabs.concord.it.runtime.v2.ITConstants.DEFAULT_TEST_TIMEOUT;
import static com.walmartlabs.concord.it.runtime.v2.Utils.resourceToString;
import static org.junit.Assert.*;

public class ProcessIT {

    @ClassRule
    public static final ConcordRule concord = ConcordConfiguration.configure();

    /**
     * Argument passing.
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testArgs() throws Exception {
        Payload payload = new Payload()
                .archive(ProcessIT.class.getResource("args").toURI())
                .arg("name", "Concord");

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        proc.assertLog(".*Runtime: concord-v2.*");
        proc.assertLog(".*Hello, Concord!.*");
    }

    /**
     * Groovy script execution.
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testGroovyScripts() throws Exception {
        Payload payload = new Payload()
                .archive(ProcessIT.class.getResource("scriptGroovy").toURI())
                .arg("name", "Concord");

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        proc.assertLog(".*Runtime: concord-v2.*");
        proc.assertLog(".*log from script: 123.*");
    }

    /**
     * Test the process metadata.
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testMetaUpdate() throws Exception {
        Payload payload = new Payload()
                .archive(ProcessIT.class.getResource("meta").toURI())
                .arg("name", "Concord");

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.expectStatus(ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        proc.assertLog(".*Runtime: concord-v2.*");
        proc.assertLog(".*Hello, Concord!.*");

        assertNotNull(pe.getMeta());
        assertEquals(3, pe.getMeta().size()); // 2 + plus system meta
        assertEquals("init-value", pe.getMeta().get("test"));
        assertEquals("xxx", pe.getMeta().get("myForm.action"));

        // ---

        List<FormListEntry> forms = proc.forms();
        assertEquals(1, forms.size());

        Map<String, Object> data = new HashMap<>();
        data.put("action", "Reject");

        FormSubmitResponse fsr = proc.submitForm(forms.get(0).getName(), data);
        assertTrue(fsr.isOk());

        pe = proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*Action: Reject.*");

        assertNotNull(pe.getMeta());
        assertEquals(3, pe.getMeta().size()); // 2 + plus system meta
        assertEquals("init-value", pe.getMeta().get("test"));
        assertEquals("Reject", pe.getMeta().get("myForm.action"));
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testOutVariables() throws Exception {
        Payload payload = new Payload()
                .archive(ProcessIT.class.getResource("out").toURI())
                .out("x", "y.some.boolean", "z");

        ConcordProcess proc = concord.processes().start(payload);

        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

        // ---

        Map<String, Object> data = proc.getOutVariables();
        assertNotNull(data);

        assertEquals(123, data.get("x"));
        assertEquals(true, data.get("y.some.boolean"));
        assertFalse(data.containsKey("z"));
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testLogsFromExpressions() throws Exception {
        Payload payload = new Payload()
                .archive(ProcessIT.class.getResource("logExpression").toURI());

        ConcordProcess proc = concord.processes().start(payload);

        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*log from expression short.*");
        proc.assertLog(".*log from expression full form.*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testProjectInfo() throws Exception {
        String orgName = "org_" + randomString();
        concord.organizations().create(orgName);

        String projectName = "project_" + randomString();
        concord.projects().create(orgName, projectName);

        Payload payload = new Payload()
                .org(orgName)
                .project(projectName)
                .archive(ProcessIT.class.getResource("projectInfo").toURI());

        ConcordProcess proc = concord.processes().start(payload);

        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*orgName=" + orgName + ".*");
        proc.assertLog(".*projectName=" + projectName + ".*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testCheckpoints() throws Exception {
        Payload payload = new Payload()
                .archive(ProcessIT.class.getResource("checkpoints").toURI());

        ConcordProcess proc = concord.processes().start(payload);
        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

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
        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testCheckpointsParallel() throws Exception {
        Payload payload = new Payload()
                .archive(ProcessIT.class.getResource("checkpointsParallel").toURI());

        ConcordProcess proc = concord.processes().start(payload);
        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

        // ---

        List<ProcessCheckpointEntry> checkpoints = proc.checkpoints();
        assertEquals(3, checkpoints.size());

        checkpoints.sort(Comparator.comparing(ProcessCheckpointEntry::getCreatedAt));

        assertEquals("third", checkpoints.get(2).getName());

        // ---

        proc.restoreCheckpoint(checkpoints.get(1).getId());
        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

        proc.assertLogAtLeast(".*#4.*z=345.*", 2);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testNoStateAfterCheckpoint() throws Exception {
        String concordYml = resourceToString(ProcessIT.class.getResource("checkpointState/concord.yml"))
                .replaceAll("PROJECT_VERSION", ITConstants.PROJECT_VERSION);

        Payload payload = new Payload().concordYml(concordYml);

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        List<ProcessCheckpointEntry> checkpoints = proc.checkpoints();
        assertEquals(1, checkpoints.size());

        proc.assertLog(".*#1 BEFORE: false.*");
        proc.assertLog(".*#2 AFTER: false.*");
    }
}
