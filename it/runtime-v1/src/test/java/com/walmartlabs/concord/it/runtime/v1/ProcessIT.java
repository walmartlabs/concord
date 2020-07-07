package com.walmartlabs.concord.it.runtime.v1;

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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.ProcessListQuery;
import ca.ibodrov.concord.testcontainers.junit4.ConcordRule;
import com.walmartlabs.concord.client.FormListEntry;
import com.walmartlabs.concord.client.FormSubmitResponse;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.ProcessEntry.StatusEnum;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.ClassRule;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static com.walmartlabs.concord.it.runtime.v1.ITConstants.DEFAULT_TEST_TIMEOUT;
import static org.junit.Assert.*;

public class ProcessIT {

    @ClassRule
    public static final ConcordRule concord = ConcordConfiguration.configure();

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testUploadAndRun() throws Exception {
        // prepare the payload
        byte[] archive = archive(ProcessIT.class.getResource("example").toURI());

        // start the process
        ConcordProcess proc = concord.processes().start(new Payload().archive(archive));

        // wait for completion
        proc.expectStatus(StatusEnum.FINISHED);

        proc.assertLog(".*Hello, world.*");
        proc.assertLog(".*Hello, local files!.*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testDefaultEntryPoint() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("defaultEntryPoint").toURI());

        ConcordProcess proc = concord.processes().start(new Payload().archive(archive));

        // wait for completion
        proc.expectStatus(StatusEnum.FINISHED);

        proc.assertLog(".*Hello, Concord!.*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testInterpolation() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("interpolation").toURI());

        // ---

        ConcordProcess proc = concord.processes().start(new Payload().archive(archive));

        // ---

        proc.expectStatus(StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*Hello, world.*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testErrorHandling() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("errorHandling").toURI());

        ConcordProcess proc = concord.processes().start(new Payload().archive(archive));

        proc.expectStatus(StatusEnum.FINISHED);

        proc.assertLog(".*Kaboom.*");
        proc.assertLog(".*We got:.*java.lang.RuntimeException.*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testStartupProblem() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("startupProblem").toURI());

        ConcordProcess proc = concord.processes().start(new Payload().archive(archive));

        proc.expectStatus(StatusEnum.FAILED);

        proc.assertLog(".*gaaarbage.*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testMultipart() throws Exception {
        String zVal = "z" + randomString();
        String myFileVal = "myFile" + randomString();
        byte[] archive = archive(ProcessIT.class.getResource("multipart").toURI());


        // ---

        Payload payload = new Payload()
                .archive(archive)
                .entryPoint("main")
                .arg("z", zVal)
                .file("myfile.txt", myFileVal.getBytes());

        ConcordProcess proc = concord.processes().start(payload);

        // ---

        proc.expectStatus(StatusEnum.FINISHED);

        proc.assertLog(".*x=123.*");
        proc.assertLog(".*y=abc.*");
        proc.assertLog(".*z=" + zVal + ".*");
        proc.assertLog(".*myfile=" + myFileVal + ".*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testWorkDir() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("workDir").toURI());

        ConcordProcess proc = concord.processes().start(new Payload().archive(archive));

        proc.expectStatus(StatusEnum.SUSPENDED);
        proc.assertLog(".*Hello!");
        proc.assertLog(".*Bye!");

        // ---

        List<FormListEntry> forms = proc.forms();
        assertEquals(1, forms.size());

        FormListEntry f = forms.get(0);
        FormSubmitResponse fsr = proc.submitForm(f.getName(), Collections.singletonMap("name", "test"));
        assertNull(fsr.getErrors());

        // ---

        proc.expectStatus(StatusEnum.FINISHED);

        proc.assertLogAtLeast(".*Hello!", 2);
        proc.assertLogAtLeast(".*Bye!", 2);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testSwitch() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("switchCase").toURI());

        ConcordProcess proc = concord.processes().start(new Payload().archive(archive));

        // ---

        proc.expectStatus(StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*234234.*");
        proc.assertLog(".*Hello, Concord.*");
        proc.assertLog(".*Bye!.*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testTags() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("example").toURI());

        ConcordProcess parent = concord.processes().start(new Payload().archive(archive));

        // ---

        parent.expectStatus(StatusEnum.FINISHED);

        // ---

        archive = archive(ProcessIT.class.getResource("tags").toURI());

        Payload payload = new Payload()
                .archive(archive)
                .parent(parent.instanceId());

        ConcordProcess child = concord.processes().start(payload);

        // ---

        child.expectStatus(StatusEnum.FINISHED);

        // ---

        List<ProcessEntry> l = parent.subprocesses("abc");
        assertTrue(l.isEmpty());

        l = parent.subprocesses("test");
        assertEquals(1, l.size());

        ProcessEntry e = l.get(0);
        assertEquals(child.instanceId(), e.getInstanceId());

        // ---

        l = concord.processes().list(ProcessListQuery.builder().addTags("xyz").build());
        assertTrue(l.isEmpty());

        l = concord.processes().list(ProcessListQuery.builder().addTags("IT").build());
        assertEquals(1, l.size());

        e = l.get(0);
        assertEquals(child.instanceId(), e.getInstanceId());
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testGetProcessForChildIds() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("processWithChildren").toURI());

        ConcordProcess proc = concord.processes().start(new Payload().archive(archive));

        // ---

        proc.expectStatus(StatusEnum.FINISHED);

        assertEquals(3, proc.getEntry("childrenIds").getChildrenIds().size());
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testKillCascade() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("killCascade").toURI());

        ConcordProcess proc = concord.processes().start(new Payload().archive(archive));

        proc.waitForChildStatus(StatusEnum.ENQUEUED, StatusEnum.PREPARING, StatusEnum.STARTING, StatusEnum.RUNNING);

        proc.killCascade();

        proc.waitForChildStatus(StatusEnum.CANCELLED, StatusEnum.FINISHED, StatusEnum.FAILED);

        List<ProcessEntry> processEntryList = proc.subprocesses();
        for (ProcessEntry pe : processEntryList) {
            assertEquals(StatusEnum.CANCELLED, pe.getStatus());
        }
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testActiveProfiles() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("activeProfiles").toURI());

        Payload payload = new Payload()
                .archive(archive)
                .activeProfiles("profileA,profileB");

        ConcordProcess proc = concord.processes().start(payload);

        // ---

        proc.expectStatus(StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*Hello from A\\+B.*");
        proc.assertLog(".*We got \\[profileA, profileB].*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testGetProcessErrorMessageFromRuntime() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("throwRuntime").toURI());

        // start the process

        ConcordProcess proc = concord.processes().start(new Payload().archive(archive));

        // wait for completion

        ProcessEntry pe = proc.expectStatus(StatusEnum.FAILED);

        assertProcessErrorMessage(pe, "BOOOM");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testGetProcessErrorMessageFromBpmnError() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("throwBpmnError").toURI());

        // start the process

        ConcordProcess proc = concord.processes().start(new Payload().archive(archive));

        // wait for completion

        ProcessEntry pe = proc.expectStatus(StatusEnum.FAILED);

        assertProcessErrorMessage(pe, ".*myBnpmError.*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testInvalidEntryPointError() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("multipart").toURI());

        // ---

        Payload payload = new Payload()
                .archive(archive)
                .entryPoint("not-found");

        ConcordProcess proc = concord.processes().start(payload);

        // wait for completion

        ProcessEntry pe = proc.expectStatus(StatusEnum.FAILED);

        assertProcessErrorMessage(pe, "Process 'not-found' not found");
    }

    @Test
    public void testFileUploadWithNonRootPath() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("fileupload").toURI());

        // ---

        Payload payload = new Payload()
                .archive(archive)
                .file("target/file1", "test from file".getBytes());

        // ---

        ConcordProcess proc = concord.processes().start(payload);

        // ---

        proc.expectStatus(StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*test from file.*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testRunnerLogLevel() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("runnerLogLevel").toURI());

        // start the process

        ConcordProcess proc = concord.processes().start(new Payload().archive(archive));

        // wait for completion

        proc.expectStatus(StatusEnum.FINISHED);

        // check the logs

        proc.assertNoLog(".*I AM A DEBUG MESSAGE.*");
        proc.assertNoLog(".*I AM AN INFO MESSAGE.*");
        proc.assertLog(".*I AM A WARNING.*");
        proc.assertLog(".*I AM AN ERROR.*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testDisableProcess() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("disableProcess").toURI());

        // ---

        ConcordProcess proc = concord.processes().start(new Payload().archive(archive));

        // ---

        proc.expectStatus(StatusEnum.FINISHED);

        // ---

        ProcessEntry pe = proc.disable();

        assertTrue(pe.isDisabled());
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testCustomJvmArgs() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("customJvmArgs").toURI());

        // ---

        ConcordProcess proc = concord.processes().start(new Payload().archive(archive));

        // ---

        proc.expectStatus(StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*Got: hello.*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testInterpolateWithVariables() throws Exception {
        byte[] archive = archive(ProcessIT.class.getResource("interpolateWithVars").toURI());

        // ---

        ConcordProcess proc = concord.processes().start(new Payload().archive(archive));

        // ---

        proc.expectStatus(StatusEnum.FINISHED);

        proc.assertLog(".*two: 2*");
    }

    /**
     * Verifies that variables changed in runtime are available in onFailure flows.
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testOnFailureVariables() throws Exception {
        ConcordProcess proc = concord.processes().start(new Payload()
                .archive(resource("onFailureVars")));

        proc.expectStatus(StatusEnum.FAILED);


        // wait for the onFailure process
        ConcordProcess onFailureProc;
        while (true) {
            List<ProcessEntry> l = proc.subprocesses();
            if (!l.isEmpty()) {
                onFailureProc = concord.processes().get(l.get(0).getInstanceId());
                break;
            }

            Thread.sleep(1000);
        }

        onFailureProc.expectStatus(StatusEnum.FINISHED);
        onFailureProc.assertLog(".*I've got xyz.*");
        onFailureProc.assertLog(".*Last error was:.*Boom!.*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testOnFailureVariables2() throws Exception {
        ConcordProcess proc = concord.processes().start(new Payload()
                .archive(resource("onFailureVars2")));

        proc.expectStatus(StatusEnum.FAILED);

        // wait for the onFailure process
        ConcordProcess onFailureProc;
        while (true) {
            List<ProcessEntry> l = proc.subprocesses();
            if (!l.isEmpty()) {
                onFailureProc = concord.processes().get(l.get(0).getInstanceId());
                break;
            }

            Thread.sleep(1000);
        }

        onFailureProc.expectStatus(StatusEnum.FINISHED);
        onFailureProc.assertLog(".*abc: Hello.*");
        onFailureProc.assertLog(".*Last error was:.*PropertyNotFoundException.*");
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

        assertTrue(error.get("message").toString().matches(expected));
    }

    private static URI resource(String name) throws URISyntaxException {
        return ProcessIT.class.getResource(name).toURI();
    }
}
