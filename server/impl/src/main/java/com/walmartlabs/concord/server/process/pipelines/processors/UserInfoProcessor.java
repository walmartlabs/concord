package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.pipelines.processors.signing.Signing;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.user.UserInfoProvider;
import com.walmartlabs.concord.server.user.UserManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Collects and stores the current user's data.
 */
public abstract class UserInfoProcessor implements PayloadProcessor {

    private final String key;
    private final UserManager userManager;
    private final Signing signing;
    private final ObjectMapper objectMapper;

    public UserInfoProcessor(String key,
                             UserManager userManager,
                             Signing signing,
                             ObjectMapper objectMapper) {

        this.key = requireNonNull(key);
        this.userManager = requireNonNull(userManager);
        this.signing = requireNonNull(signing);
        this.objectMapper = requireNonNull(objectMapper);
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        var info = userManager.getCurrentUserInfo();

        if (signing.isEnabled()) {
            var signature = Optional.ofNullable(info.username())
                    .map(this::sign);

            info = signature.isEmpty() ? info : UserInfoProvider.UserInfo.builder()
                    .from(info)
                    .usernameSignature(signature.get())
                    .build();
        }

        Map<String, UserInfoProvider.UserInfo> m = new HashMap<>();
        m.put(key, info);

        payload = payload.mergeValues(Payload.CONFIGURATION, m);

        return chain.process(payload);
    }

    private String sign(String username) {
        try {
            return signing.sign(username);
        } catch (Exception e) {
            throw new ConcordApplicationException("Error while singing process data: " + e.getMessage(), e);
        }
    }
}
