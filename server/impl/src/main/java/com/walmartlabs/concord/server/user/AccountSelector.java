package com.walmartlabs.concord.server.user;

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

import com.typesafe.config.Config;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.identity.IdentityDao;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.UUID;

/** Boundary validation for explicit and transitional account selectors. */
public class AccountSelector {
    private final IdentityDao identities;
    private final boolean legacyEnabled;

    @Inject
    public AccountSelector(IdentityDao identities, Config config) {
        this.identities = identities;
        String qualified = "com.walmartlabs.concord.server.security.legacy.enabled";
        String plain = "security.legacy.enabled";
        this.legacyEnabled = config.hasPath(qualified) ? config.getBoolean(qualified)
                : !config.hasPath(plain) || config.getBoolean(plain);
    }

    public boolean legacyEnabled() {
        return legacyEnabled;
    }

    public void requireLegacy() {
        if (!legacyEnabled) {
            throw new ValidationErrorsException("Legacy user selectors are disabled");
        }
    }

    public void validate(UUID userId, ExternalIdentityKey identity, boolean legacyFieldsPresent) {
        if (legacyFieldsPresent) requireLegacy();
        if (identity != null && (userId != null || legacyFieldsPresent)) {
            throw new ValidationErrorsException("Identity cannot be combined with other user selectors");
        }
    }

    public UUID identityId(ExternalIdentityKey identity) {
        return identities.find(identity).filter(r -> r.active() && r.userId() != null)
                .map(IdentityDao.IdentityRecord::userId)
                .orElseThrow(() -> new ConcordApplicationException("User identity not found", Response.Status.NOT_FOUND));
    }
}
