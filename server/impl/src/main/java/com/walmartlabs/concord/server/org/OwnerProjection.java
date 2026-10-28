package com.walmartlabs.concord.server.org;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.server.user.AccountSelector;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.DSLContext;
import org.jooq.Record;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Projects owner metadata without treating the legacy user type as an identity. */
public class OwnerProjection {
    private final AccountSelector selector;

    @Inject
    public OwnerProjection(AccountSelector selector) {
        this.selector = selector;
    }

    public EntityOwner owner(DSLContext tx, UUID id, String username, String domain, String displayName, String userType) {
        if (id == null) return null;
        Record r = tx.fetchOne("select profile_provider_id, array(select distinct provider_id from user_identities where user_id = ? and is_active) from users where user_id = ?", id, id);
        String providerId = r != null ? r.get(0, String.class) : null;
        String[] providers = r != null ? r.get(1, String[].class) : null;
        Set<String> identityProviderIds = providers != null ? Arrays.stream(providers).collect(Collectors.toSet()) : null;
        return EntityOwner.builder().id(id).username(username).userDomain(domain).displayName(displayName)
                .userType(selector.legacyEnabled() ? UserType.fromNullable(userType) : null)
                .profileProviderId(providerId).identityProviderIds(identityProviderIds).build();
    }
}
