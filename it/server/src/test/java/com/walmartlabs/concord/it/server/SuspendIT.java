package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.ProcessStatusResponse;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

public class SuspendIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        URI dir = SuspendIT.class.getResource("suspend").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload));

        // ---

        ProcessStatusResponse pir = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.SUSPENDED);

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*aaaa.*", ab);

        // ---

        String testValue = "test#" + System.currentTimeMillis();
        Map<String, Object> args = Collections.singletonMap("testValue", testValue);
        Map<String, Object> req = Collections.singletonMap(Constants.ARGUMENTS_KEY, args);

        processResource.resume(spr.getInstanceId(), "ev1", req);
        waitForLog(pir.getLogFileName(), ".*resuming.*");

        pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        waitForLog(pir.getLogFileName(), ".*bbbb.*");
        waitForLog(pir.getLogFileName(), ".*" + Pattern.quote(testValue) + ".*");
    }
}
