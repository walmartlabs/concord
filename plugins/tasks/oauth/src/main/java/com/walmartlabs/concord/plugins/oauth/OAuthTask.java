package com.walmartlabs.concord.plugins.oauth;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.plugins.oauth.client.GoogleOAuthClient;
import com.walmartlabs.concord.plugins.oauth.client.GithubOAuthClient;
import com.walmartlabs.concord.plugins.oauth.client.OAuthClient;
import com.walmartlabs.concord.plugins.oauth.client.model.TokenResponse;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Named("oauth")
public class OAuthTask implements ReentrantTask {

    private static final Logger log = LoggerFactory.getLogger(OAuthTask.class);
    private static final String OAUTH_AUTHORIZATION_CODE_KEY = "oAuthAuthorizationCode";

    private final SecretService secretService;

    private final Context context;

    @Inject
    public OAuthTask(Context context) {
        this.secretService = context.secretService();
        this.context = context;
    }

    @Override
    public TaskResult execute(Variables input) throws URISyntaxException {
        TaskParams taskParams = new TaskParams(input);
        OAuthClient oAuthClient = getOAuthClient(taskParams);

        String eventName = UUID.randomUUID().toString();
        String state = generateState(eventName);

        String authorizationUrl = oAuthClient.buildAuthorizationUrl(taskParams.oAuthProviderInfo().clientId(),
                state,
                taskParams.oAuthProviderInfo().scope());

        log.info("Please click here to login to your account {}", authorizationUrl);
        return TaskResult.reentrantSuspend(eventName, taskParams.asMap());
    }

    private String generateState(String eventName) {
        String sessionToken = context.processConfiguration().processInfo().sessionToken();
        String instanceId = context.processInstanceId().toString();
        return Base64.getEncoder().encodeToString((instanceId + "|" + eventName + "|" + sessionToken).getBytes());
    }

    private OAuthClient getOAuthClient(TaskParams taskParams) throws URISyntaxException {
        switch (taskParams.oAuthProvider()) {
            case GITHUB -> {
                return new GithubOAuthClient(context.apiConfiguration().baseUrl(),
                        taskParams.oAuthProviderInfo().authorityUrl());
            }
            case AZURE -> {
                return new GoogleOAuthClient(context.apiConfiguration().baseUrl(),
                        taskParams.oAuthProviderInfo().authorityUrl());
            }
        }
        throw new RuntimeException("Invalid OAuth provider");
    }

    @Override
    public TaskResult resume(ResumeEvent event) throws Exception {
        String authorizationCode = context.variables().assertString(OAUTH_AUTHORIZATION_CODE_KEY);
        Map<String, Object> state = new HashMap<>(event.state());
        TaskParams taskParams = new TaskParams(new MapBackedVariables(state));

        String clientSecret = secretService.exportAsString(taskParams.clientSecret().org(),
                taskParams.clientSecret().secret(),
                taskParams.clientSecret().password());

        TokenResponse tokenResponse = getOAuthClient(taskParams).getAccessToken(taskParams.clientId(),
                clientSecret,
                authorizationCode);

        return TaskResult.success().value("tokenResponse", tokenResponse);
    }


}
