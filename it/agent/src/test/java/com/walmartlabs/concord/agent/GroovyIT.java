package com.walmartlabs.concord.agent;

import com.google.common.io.ByteStreams;
import com.walmartlabs.concord.agent.api.AgentResource;
import com.walmartlabs.concord.agent.api.JobStatus;
import com.walmartlabs.concord.agent.api.JobType;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@Ignore
public class GroovyIT {

    private Main main;
    private Client client;

    @Before
    public void setUp() throws Exception {
        main = new Main();
        main.start(0);

        client = ClientBuilder.newClient();
    }

    @After
    public void tearDown() throws Exception {
        client.close();
        main.stop();
    }

    @Test(timeout = 15000)
    public void test() throws Exception {
        ResteasyWebTarget target = (ResteasyWebTarget) client.target("http://localhost:" + main.getLocalPort());
        AgentResource proxy = target.proxy(AgentResource.class);

        String resource = "test.groovy";
        InputStream payload = makePayload(resource);

        // start a job
        String id = UUID.randomUUID().toString();
        proxy.start(id, JobType.JUNIT_GROOVY, resource, payload);

        // check the job's status
        JobStatus s = proxy.getStatus(id);
        assertNotEquals(JobStatus.FAILED, s);

        // count active jobs
        assertEquals(1, proxy.count());

        // cancel the job
        proxy.cancel(id, true);

        // check if was actually cancelled
        s = proxy.getStatus(id);
        assertEquals(JobStatus.CANCELLED, s);
    }

    protected static InputStream makePayload(String resource) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipOutputStream out = new ZipOutputStream(baos);
             InputStream src = GroovyIT.class.getResourceAsStream(resource)) {

            ZipEntry e = new ZipEntry(resource);
            out.putNextEntry(e);
            ByteStreams.copy(src, out);
        }

        return new ByteArrayInputStream(baos.toByteArray());
    }
}
