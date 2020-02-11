package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.StartProcessResponse;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;

public class DispatcherIT extends AbstractServerIT {

    /**
     * Tests the behaviour of the process queue dispatcher when one of
     * the required agent types is not available.
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testUnknownFlavor() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("unknownFlavor").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("requirements.agent.type", randomString());
        input.put("archive", payload);

        StartProcessResponse unknownFlavor = start(input);

        // ---

        input.put("requirements.agent.type", "test"); // as in it/server/src/test/resources/agent.conf

        StartProcessResponse knownFlavor = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForCompletion(processApi, knownFlavor.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        pe = processApi.get(unknownFlavor.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.ENQUEUED, pe.getStatus());

        processApi.kill(pe.getInstanceId());
    }
}
