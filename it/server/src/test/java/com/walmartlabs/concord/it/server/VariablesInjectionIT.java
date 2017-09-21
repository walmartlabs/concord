package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertNotNull;

public class VariablesInjectionIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        byte[] payload = archive(VariablesInjectionIT.class.getResource("inject").toURI(),
                ITConstants.DEPENDENCIES_DIR);

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Hello, Concord!.*", ab);
        assertLog(".*Hello, world!!!.*", ab);
    }
}
