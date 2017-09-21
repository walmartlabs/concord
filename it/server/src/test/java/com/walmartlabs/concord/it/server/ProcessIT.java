package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.*;
import org.junit.Ignore;
import org.junit.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.*;

public class ProcessIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testUploadAndRun() throws Exception {
        // prepare the payload

        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        // start the process

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello, world.*", ab);
        assertLog(".*Hello, local files!.*", ab);
    }

    @Test
    @Ignore
    public void testLotsOfProcesses() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        int count = 100;
        for (int i = 0; i < count; i++) {
            ProcessResource processResource = proxy(ProcessResource.class);
            processResource.start(new ByteArrayInputStream(payload), null, false);
        }
    }

    @Test(timeout = 30000)
    public void testUploadAndRunSync() throws Exception {
        // prepare the payload

        byte[] payload = archive(ProcessIT.class.getResource("process-sync").toURI());

        // start the process

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, true);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = processResource.get(spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*100223.*", ab);
        assertLog(".*Boo Zoo.*", ab);
        assertLog(".*122.*", ab);
        assertLog(".*100323.*", ab);
        assertLog(".*red.*", ab);

        assertTrue(pir.getStatus() == ProcessStatus.FINISHED);
    }

    @Test(timeout = 60000)
    public void testTimeout() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("timeout").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false);

        try {
            processResource.waitForCompletion(spr.getInstanceId(), 3000);
            fail("should fail");
        } catch (WebApplicationException e) {
            Response r = e.getResponse();
            ProcessEntry pir = r.readEntity(ProcessEntry.class);
            assertEquals(ProcessStatus.RUNNING, pir.getStatus());
        }

        processResource.kill(spr.getInstanceId());

        waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.CANCELLED, ProcessStatus.FAILED, ProcessStatus.FINISHED);
    }

    @Test(timeout = 30000)
    public void testInterpolation() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("interpolation").toURI());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false);
        assertNotNull(spr.getInstanceId());

        // ---

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello, world.*", ab);
    }

    @Test(timeout = 30000)
    public void testErrorHandling() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("errorHandling").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Kaboom.*", ab);
        assertLog(".*We got:.*java.lang.RuntimeException.*", ab);
    }

    @Test(timeout = 30000)
    public void testStartupProblem() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("startupProblem").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.FAILED);

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*gaaarbage.*", ab);
    }

    @Test(timeout = 30000)
    public void testMultipart() throws Exception {
        String zVal = "z" + System.currentTimeMillis();
        String myFileVal = "myFile" + System.currentTimeMillis();
        byte[] payload = archive(ProcessIT.class.getResource("multipart").toURI());


        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("entryPoint", "main");
        input.put("arguments.z", zVal);
        input.put("myfile.txt", myFileVal.getBytes());

        StartProcessResponse spr = start(input);
        assertNotNull(spr.getInstanceId());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.FINISHED);

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*x=123.*", ab);
        assertLog(".*y=abc.*", ab);
        assertLog(".*z=" + zVal + ".*", ab);
        assertLog(".*myfile=" + myFileVal + ".*", ab);
    }

    @Test(timeout = 30000)
    public void testWorkDir() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("workDir").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.SUSPENDED);
        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello!", ab);
        assertLog(".*Bye!", ab);

        // ---

        FormResource formResource = proxy(FormResource.class);
        List<FormListEntry> forms = formResource.list(pir.getInstanceId());
        assertEquals(1, forms.size());

        FormListEntry f = forms.get(0);
        FormSubmitResponse fsr = formResource.submit(pir.getInstanceId(), f.getFormInstanceId(), Collections.singletonMap("name", "test"));
        assertNull(fsr.getErrors());

        // ---

        pir = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.FINISHED);
        ab = getLog(pir.getLogFileName());
        assertLogAtLeast(".*Hello!", 2, ab);
        assertLogAtLeast(".*Bye!", 2, ab);
    }

    @Test(timeout = 30000)
    public void testSwitch() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("switchCase").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false);

        // ---

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*234234.*", ab);
        assertLog(".*Hello, Concord.*", ab);
        assertLog(".*Bye!.*", ab);
    }

    @Test(timeout = 30000)
    public void testTaskOut() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("taskOut").toURI(), ITConstants.DEPENDENCIES_DIR);

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), false);

        // ---

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*I said: Hello!.*", ab);
    }

    @Test(timeout = 30000)
    public void testDelegateOut() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("delegateOut").toURI(), ITConstants.DEPENDENCIES_DIR);

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), false);

        // ---

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*I said: Hello!.*", ab);
    }
}
