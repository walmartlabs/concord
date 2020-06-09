package com.walmartlabs.concord.server.events;

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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;

import java.util.Map;
import java.util.function.Supplier;

public class EventInitiatorSupplier implements Supplier<UserEntry> {

    private final String initiatorAttr;
    private final UserManager userManager;
    private final Map<String, Object> eventAttributes;

    public EventInitiatorSupplier(String initiatorAttr, UserManager userManager, Map<String, Object> eventAttributes) {
        this.initiatorAttr = initiatorAttr;
        this.userManager = userManager;
        this.eventAttributes = eventAttributes;
    }

    @Override
    public UserEntry get() {
        Object author = ConfigurationUtils.get(eventAttributes, initiatorAttr);
        if (author == null) {
            return null;
        }

        return userManager.getOrCreate(author.toString(), null, UserType.LDAP)
                .orElseThrow(() -> new ConcordApplicationException("User not found: " + author));
    }
}
