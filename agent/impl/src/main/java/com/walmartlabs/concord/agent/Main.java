package com.walmartlabs.concord.agent;

public class Main {

    public void start() throws Exception {
        Configuration cfg = new Configuration();
        ServerConnector c = new ServerConnector();
        c.start(cfg);

        Thread.currentThread().join();
    }

    public static void main(String[] args) throws Exception {
        Main m = new Main();
        m.start();
    }
}
