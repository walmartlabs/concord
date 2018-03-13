package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;

public class ProcessStateIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testSingleFile() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("stateSingleFile").toURI());

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pe = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pe.getStatus());

        // ---

        Response resp = processResource.downloadState(spr.getInstanceId(), "concord.yml");
        assertEquals(200, resp.getStatus());
    }
}
