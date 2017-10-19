package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.sdk.ApiConfiguration;

import javax.inject.Named;

import static com.walmartlabs.concord.runner.ConfigurationUtils.getEnv;

@Named
public class ApiConfigurationImpl implements ApiConfiguration {

    private static final String BASE_URL_KEY = "api.baseUrl";

    private final String baseUrl;

    public ApiConfigurationImpl() {
        this.baseUrl = getEnv(BASE_URL_KEY, "http://localhost:8001");
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }
}
