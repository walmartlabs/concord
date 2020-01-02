package com.walmartlabs.concord.plugins.slack;

public enum TaskParams {

    ACTION("action"),
    API_TOKEN("apiToken"),
    ATTACHMENTS("attachments"),
    AUTH_TOKEN("authToken"),
    CHANNEL_ID("channelId"),
    CHANNEL_NAME("channelName"),
    CONNECT_TIMEOUT("connectTimeout"),
    ICON_EMOJI("iconEmoji"),
    IGNORE_ERRORS("ignoreErrors"),
    PROXY_ADDRESS("proxyAddress"),
    PROXY_PORT("proxyPort"),
    REACTION("reaction"),
    RETRY_COUNT("retryCount"),
    SO_TIMEOUT("soTimeout"),
    TEXT("text"),
    TS("ts"),
    USERNAME("username");

    private final String key;

    TaskParams(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
