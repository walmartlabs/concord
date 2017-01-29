package com.walmartlabs.concord.it.server;

import org.junit.Test;

import java.net.URL;
import java.net.URLConnection;

public class SimpleIT {

    @Test
    public void test() throws Exception {
        URL url = new URL(ITConstants.SERVER_URL + "/api/v1/server/ping");
        URLConnection conn = url.openConnection();
        System.out.println(conn.getContent());
    }
}
