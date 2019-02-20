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

import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.pipelines.processors.signing.Signing;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.security.ldap.UserInfo;
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
    private final LdapManager ldapManager;
    private final Signing signing;

    public UserInfoProcessor(String key, LdapManager ldapManager, Signing signing) {
        this.key = key;
        this.ldapManager = ldapManager;
        this.signing = signing;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        UserInfo info = ldapManager.getCurrentUserInfo();

        if (signing.isEnabled()) {
            info = sign(info);
        }

        Map<String, UserInfo> m = new HashMap<>();
        m.put(key, info);

        payload = payload.mergeValues(Payload.REQUEST_DATA_MAP, m);

        log.info("process ['{}'] -> done", payload.getProcessKey());
        return chain.process(payload);
    }

    private UserInfo sign(UserInfo i) {
        if (i == null || i.getUsername() == null) {
            return i;
        }

        try {
            String s = signing.sign(i.getUsername());
            return new SignedUserInfo(i, s);
        } catch (Exception e) {
            throw new ConcordApplicationException("Error while singing process data: " + e.getMessage(), e);
        }
    }

    public static class SignedUserInfo extends UserInfo {

        private static final long serialVersionUID = 1L;

        private final String usernameSignature;

        public SignedUserInfo(UserInfo source,
                              String usernameSignature) {

            super(source.getUsername(), source.getDisplayName(), source.getGroups(), source.getAttributes());
            this.usernameSignature = usernameSignature;
        }

        public String getUsernameSignature() {
            return usernameSignature;
        }
    }
}
