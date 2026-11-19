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

import com.walmartlabs.concord.server.cfg.ExternalIdentityConfiguration;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityException;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityRef;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentitySnapshot;

import javax.inject.Inject;
import java.time.Duration;
import java.util.UUID;

/**
 * Checks lifecycle eligibility only, not resource permissions or delegation.
 * Never provisions users, enables accounts or establishes execution authority.
 */
public class ExternalIdentityLifecycleManager {

    private final ExternalIdentityDao dao;
    private final ExternalIdentitySynchronizer synchronizer;
    private final Duration maxSnapshotAge;

    @Inject
    public ExternalIdentityLifecycleManager(ExternalIdentityDao dao, ExternalIdentitySynchronizer synchronizer,
                                            ExternalIdentityConfiguration cfg) {
        this.dao = dao;
        this.synchronizer = synchronizer;
        this.maxSnapshotAge = cfg.getMaxSnapshotAge();
        if (maxSnapshotAge == null || maxSnapshotAge.isNegative() || maxSnapshotAge.isZero()) {
            throw new IllegalArgumentException("externalIdentity.maxSnapshotAge must be a positive duration");
        }
    }

    public UUID requireActive(ExternalIdentityRef identity) throws ExternalIdentityException {
        synchronizer.assertProviderRegistered(identity);
        var state = requireEnabledAccount(identity);
        var userId = state.userId();
        if (!isFresh(state)) {
            synchronizer.refresh(identity);
            state = requireEnabledAccount(identity);
            if (!userId.equals(state.userId())) {
                throw new ExternalIdentityException("External identity binding changed during lifecycle verification");
            }
        }
        if (!isFresh(state)) {
            throw new ExternalIdentityException("External identity snapshot is not fresh after refresh");
        }
        if (state.status() != ExternalIdentitySnapshot.Status.ACTIVE) {
            throw new ExternalIdentityException("External identity source is not active: " + state.status());
        }
        return userId;
    }

    private ExternalIdentityDao.LifecycleState requireEnabledAccount(ExternalIdentityRef identity) throws ExternalIdentityException {
        var state = dao.getLifecycleState(identity)
                .orElseThrow(() -> new ExternalIdentityException("External identity is not provisioned"));
        if (state.userType() != UserType.EXTERNAL) {
            throw new ExternalIdentityException("External identity binding does not refer to an external user");
        }
        if (state.disabled() || state.permanentlyDisabled()) {
            throw new ExternalIdentityException("External identity Concord account is disabled");
        }
        return state;
    }

    private boolean isFresh(ExternalIdentityDao.LifecycleState state) {
        if (state.status() == null || state.lastSuccessfulSync() == null) {
            return false;
        }
        var age = Duration.between(state.lastSuccessfulSync(), state.checkedAt());
        return !age.isNegative() && age.compareTo(maxSnapshotAge) < 0;
    }
}
