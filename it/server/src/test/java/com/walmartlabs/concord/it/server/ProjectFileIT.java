package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatusResponse;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertNotNull;

public class ProjectFileIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testSingleProfile() throws Exception {
        test("projectfile/singleprofile");
    }

    @Test(timeout = 30000)
    public void testOverrideFlow() throws Exception {
        test("projectfile/overrideflow");
    }

    private void test(String resource) throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource(resource).toURI());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload));
        assertNotNull(spr.getInstanceId());

        ProcessStatusResponse pir = waitForCompletion(processResource, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello, world.*", ab);
    }
}
