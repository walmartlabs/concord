package com.walmartlabs.concord.sdk;

public interface ApiConfiguration {

    String getBaseUrl();

    String getSessionToken(Context ctx);
}
