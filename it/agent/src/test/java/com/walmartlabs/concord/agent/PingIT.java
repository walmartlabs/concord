package com.walmartlabs.concord.agent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;

public class PingIT {

    private Main main;
    private Client client;

    @Before
    public void setUp() throws Exception {
        main = new Main();
        main.start();

        client = ClientBuilder.newClient();
    }

    @After
    public void tearDown() throws Exception {
        client.close();
        main.stop();
    }

    @Test
    public void test() throws Exception {
        WebTarget t = client.target("http://localhost:8002/api/v1/agent/ping");
        Response resp = t.request().get();
        assertEquals(200, resp.getStatus());
    }
}
