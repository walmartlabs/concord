package com.walmartlabs.concord.server.org.secret.store.concord;

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

import com.walmartlabs.concord.server.cfg.ConcordSecretStoreConfiguration;
import com.walmartlabs.concord.server.org.secret.SecretDao;
import com.walmartlabs.concord.server.org.secret.store.SecretStore;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.util.UUID;

public class ConcordSecretStore implements SecretStore {

    private static final String TYPE = "concord";
    private static final String DESCRIPTION = "Concord";

    private final boolean enabled;
    private final SecretDao secretDao;

    @Inject
    public ConcordSecretStore(ConcordSecretStoreConfiguration cfg, SecretDao secretDao) {
        this.enabled = cfg.isEnabled();
        this.secretDao = secretDao;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void store(DSLContext tx, UUID id, byte[] data) {
        secretDao.updateData(tx, id, data);
    }

    @Override
    public void delete(DSLContext tx, UUID id) {
        // do nothing, the data will be deleted when the secret's entry is removed from the DB table
    }

    @Override
    public byte[] get(UUID id) {
        return secretDao.getData(id);
    }
}
