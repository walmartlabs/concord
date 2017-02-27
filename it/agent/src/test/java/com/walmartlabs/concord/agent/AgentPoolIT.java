package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.agent.api.AgentResource;
import com.walmartlabs.concord.agent.pool.AgentPool;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AgentPoolIT {

    private Main main;

    @Before
    public void setUp() throws Exception {
        main = new Main();
        main.start(0);
    }

    @After
    public void tearDown() throws Exception {
        main.stop();
    }

    @Test
    public void testOk() throws Exception {
        URI host = URI.create("http://localhost:" + main.getLocalPort());
        AgentPool pool = new AgentPool(Collections.singleton(host));

        AgentResource a = pool.acquire(5000);
        assertEquals(0, a.count());

        pool.close();

        try {
            pool.acquire(5000);
            fail("Should fail");
        } catch (Exception e) {
        }
    }

    @Test
    public void testConnectionError() throws Exception {
        URI host = URI.create("http://localhost:65432");
        AgentPool pool = new AgentPool(Collections.singleton(host));
        try {
            pool.acquire(5000);
            fail("Should fail");
        } catch (Exception e) {
        }
    }
}
