package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.sdk.ApiConfiguration;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;

import javax.inject.Named;

import java.util.Map;

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

     @SuppressWarnings("unchecked")
     public String getSessionToken(Context ctx) {
         Map<String, Object> processInfo = (Map<String, Object>) ctx.getVariable(Constants.Request.PROCESS_INFO_KEY);
         if (processInfo == null) {
             return null;
         }

         return (String) processInfo.get("sessionKey");
     }
}
