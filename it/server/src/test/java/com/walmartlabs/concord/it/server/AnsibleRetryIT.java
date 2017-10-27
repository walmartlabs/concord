package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.net.URI;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;

public class AnsibleRetryIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testSaveRetry() throws Exception {
        URI uri = ProcessIT.class.getResource("ansibleSaveRetry").toURI();
        byte[] payload = archive(uri, ITConstants.DEPENDENCIES_DIR);

        // start the process

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);

        // wait for completion

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FAILED, pir.getStatus());

        // retrieve the retry file

        Response r = processResource.downloadAttachment(pir.getInstanceId(), "ansible.retry");
        assertEquals(200, r.getStatus());
    }
}
