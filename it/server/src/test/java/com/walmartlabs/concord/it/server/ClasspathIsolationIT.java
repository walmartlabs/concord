package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;

public class ClasspathIsolationIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testBrokenDeps() throws Exception {
        byte[] payload = archive(ClasspathIsolationIT.class.getResource("brokenDeps").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*hello!.*", ab);
    }
}
