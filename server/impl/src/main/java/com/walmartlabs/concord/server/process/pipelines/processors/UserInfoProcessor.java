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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.user.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public abstract class UserInfoProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(UserInfoProcessor.class);

    private final LdapManager ldapManager;

    private final String key;

    public UserInfoProcessor(String key, LdapManager ldapManager) {
        this.key = key;
        this.ldapManager = ldapManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        // collect and store the initiator's data

        UserInfo info = getInfo();
        Map<String, UserInfo> m = new HashMap<>();
        m.put(key, info);

        payload = payload.mergeValues(Payload.REQUEST_DATA_MAP, m);

        log.info("process ['{}'] -> done", payload.getProcessKey());
        return chain.process(payload);
    }

    private UserInfo getInfo() {
        UserPrincipal p = UserPrincipal.getCurrent();
        if (p == null) {
            return null;
        }

        LdapPrincipal l = LdapPrincipal.getCurrent();
        if (l == null && p.getType() == UserType.LDAP) {
            try {
                l = ldapManager.getPrincipal(p.getUsername());
            } catch (NamingException e) {
                log.warn("getInfo -> error while retrieving LDAP information for '{}': {}", p.getUsername(), e.getMessage());
            }
        }

        if (l == null) {
            return new UserInfo(p.getUsername(), p.getUsername(), Collections.emptySet(), Collections.emptyMap());
        }

        return new UserInfo(p.getUsername(), l.getDisplayName(), l.getGroups(), l.getAttributes());
    }

    @JsonInclude(Include.NON_NULL)
    public static class UserInfo implements Serializable {

        private final String username;
        private final String displayName;
        private final Set<String> groups;
        private final Map<String, String> attributes;

        public UserInfo(String username, String displayName, Set<String> groups, Map<String, String> attributes) {
            this.username = username;
            this.displayName = displayName;
            this.groups = groups;
            this.attributes = attributes;
        }

        public String getUsername() {
            return username;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Set<String> getGroups() {
            return groups;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }
    }
}
