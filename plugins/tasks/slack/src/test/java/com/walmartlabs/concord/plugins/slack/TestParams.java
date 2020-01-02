package com.walmartlabs.concord.plugins.slack;

import java.util.Optional;

public final class TestParams {

    public static final String TEST_API_TOKEN = System.getenv("SLACK_TEST_API_TOKEN");
    public static final String TEST_PROXY_ADDRESS = System.getenv("SLACK_TEST_PROXY_ADDRESS");
    public static final String TEST_INVALID_PROXY_ADDRESS = System.getenv("SLACK_TEST_INVALID_PROXY_ADDRESS");
    public static final int TEST_PROXY_PORT = Integer.parseInt(Optional.ofNullable(System.getenv("SLACK_TEST_PROXY_PORT")).orElse("-1"));
    public static final String TEST_CHANNEL = System.getenv("SLACK_TEST_CHANNEL");

    private TestParams() {
    }
}
