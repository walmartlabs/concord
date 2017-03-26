package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.ProcessStatusResponse;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;

import static org.junit.Assert.*;

public class ProcessIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testUploadAndRun() throws Exception {
        // prepare the payload

        byte[] payload = archive(ProcessIT.class.getResource("example").toURI());

        // start the process

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload));
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessStatusResponse pir = waitForCompletion(processResource, spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello, world.*", ab);
        assertLog(".*Hello, local files!.*", ab);
    }

    @Test(timeout = 60000)
    public void testTimeout() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("timeout").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload));

        try {
            processResource.waitForCompletion(spr.getInstanceId(), 3000);
            fail("should fail");
        } catch (WebApplicationException e) {
            Response r = e.getResponse();
            ProcessStatusResponse pir = r.readEntity(ProcessStatusResponse.class);
            assertEquals(ProcessStatus.RUNNING, pir.getStatus());
        }

        processResource.kill(spr.getInstanceId());
    }
}
