package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;
import static org.junit.Assert.*;

public class ProcessIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testUploadAndRun() throws Exception {
        // prepare the payload

        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        // start the process

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), false);
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

    @Test(timeout = 30000)
    public void testUploadAndRunSync() throws Exception {
        // prepare the payload

        byte[] payload = archive(ProcessIT.class.getResource("process-sync").toURI());

        // start the process

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), true);
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
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), false);

        try {
            processResource.waitForCompletion(spr.getInstanceId(), 3000);
            fail("should fail");
        } catch (WebApplicationException e) {
            Response r = e.getResponse();
            ProcessEntry pir = r.readEntity(ProcessEntry.class);
            assertEquals(ProcessStatus.RUNNING, pir.getStatus());
        }

        processResource.kill(spr.getInstanceId());
    }

    @Test(timeout = 30000)
    public void testInterpolation() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("interpolation").toURI());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), false);
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
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), false);
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
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), false);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.FAILED);

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*gaaarbage.*", ab);
    }
}
