package com.walmartlabs.concord.it.runtime.v2;

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit4.ConcordRule;
import com.walmartlabs.concord.client.ProcessEntry;
import org.junit.Rule;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static com.walmartlabs.concord.it.runtime.v2.ITConstants.DEFAULT_TEST_TIMEOUT;
import static com.walmartlabs.concord.it.runtime.v2.ITConstants.DOCKER_ANSIBLE_IMAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DockerIT {

    @Rule
    public final ConcordRule concord = ConcordConfiguration.configure();

    /**
     * Ensure docker service can capture standard error output
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testDockerLogCaptureLimit() throws Exception {
        Payload payload = new Payload()
                .archive(resource("dockerCaptureLimit"))
                .arg("image", DOCKER_ANSIBLE_IMAGE);

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        proc.assertLog(".*stdout loop 10000.*");
        proc.assertLog(".*stderr loop 10000.*");
    }

    private static URI resource(String name) throws URISyntaxException {
        URL url = DockerIT.class.getResource(name);
        assertNotNull("can't find '" + name + "'", url);
        return url.toURI();
    }
}
