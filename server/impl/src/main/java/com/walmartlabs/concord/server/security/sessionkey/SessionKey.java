package com.walmartlabs.concord.server.security.sessionkey;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import org.apache.shiro.authc.AuthenticationToken;

import java.util.UUID;

public class SessionKey implements AuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final UUID instanceId;

    public SessionKey(UUID instanceId) {
        this.instanceId = instanceId;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    @Override
    public Object getPrincipal() {
        return getInstanceId();
    }

    @Override
    public Object getCredentials() {
        return getInstanceId();
    }

    @Override
    public String toString() {
        return "SessionKey{" +
                "instanceId=" + instanceId +
                '}';
    }
}
