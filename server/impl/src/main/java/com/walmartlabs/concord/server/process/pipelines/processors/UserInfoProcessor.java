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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.pipelines.processors.signing.Signing;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.user.UserInfoProvider.BaseUserInfo;
import com.walmartlabs.concord.server.user.UserManager;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Collects and stores the current user's data.
 */
public abstract class UserInfoProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(UserInfoProcessor.class);

    private final String key;
    private final UserManager userManager;
    private final Signing signing;

    public UserInfoProcessor(String key, UserManager userManager, Signing signing) {
        this.key = key;
        this.userManager = userManager;
        this.signing = signing;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        BaseUserInfo info = userManager.getCurrentUserInfo();

        if (signing.isEnabled()) {
            info = sign(info);
        }

        Map<String, BaseUserInfo> m = new HashMap<>();
        m.put(key, info);

        payload = payload.mergeValues(Payload.CONFIGURATION, m);

        log.info("process -> done");
        return chain.process(payload);
    }

    private BaseUserInfo sign(BaseUserInfo i) {
        if (i == null || i.username() == null) {
            return i;
        }

        try {
            String s = signing.sign(i.username());
            return SignedUserInfo.from(i).usernameSignature(s).build();
        } catch (Exception e) {
            throw new ConcordApplicationException("Error while singing process data: " + e.getMessage(), e);
        }
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableSignedUserInfo.class)
    @JsonDeserialize(as = ImmutableSignedUserInfo.class)
    public interface SignedUserInfo extends BaseUserInfo {

        String usernameSignature();

        public static ImmutableSignedUserInfo.Builder from(BaseUserInfo i) {
            return ImmutableSignedUserInfo.builder().from(i);
        }
    }
}
