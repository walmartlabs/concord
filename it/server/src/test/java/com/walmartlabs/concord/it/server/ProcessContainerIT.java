package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.StartProcessResponse;
import com.walmartlabs.concord.common.IOUtils;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ProcessContainerIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void test() throws Exception {
        Path src = Paths.get(DockerIT.class.getResource("processContainer").toURI());
        Path dst = IOUtils.createTempDir("test");
        IOUtils.copy(src, dst);

        Path concordYml = dst.resolve("concord.yml");
        String s = new String(Files.readAllBytes(concordYml));
        s = s.replaceAll("%%image%%", ITConstants.DOCKER_ANSIBLE_IMAGE);
        Files.write(concordYml, s.getBytes());

        // ---

        byte[] payload = archive(dst.toUri());

        StartProcessResponse spr = start(payload);

        // --

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertNotNull(pir.getLogFileName());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello from Docker!.*", ab);
    }
}
