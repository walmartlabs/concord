package com.walmartlabs.concord.agent;

import java.util.concurrent.Executors;

public class Main {

    public void start() throws Exception {
        Configuration cfg = new ConfigurationProvider().get();

        ServerConnector c = new ServerConnector(Executors.newCachedThreadPool());
        c.start(cfg);

        Thread.currentThread().join();
    }

    public static void main(String[] args) throws Exception {
        Main m = new Main();
        m.start();
    }
}
