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

import com.walmartlabs.concord.server.org.secret.SecretStoreType;
import com.walmartlabs.concord.server.org.secret.SecretDao;
import com.walmartlabs.concord.server.org.secret.store.SecretStore;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named("concordSecretStore")
public class ConcordSecretStore implements SecretStore {

    public static final String DESCRIPTION = "Concord";
    public static final String CONFIG_PREFIX = "concord";

    private final SecretDao secretDao;

    @Inject
    public ConcordSecretStore(SecretDao secretDao) {
        this.secretDao = secretDao;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public SecretStoreType getType() {
        return SecretStoreType.CONCORD;
    }

    @Override
    public String getConfigurationPrefix() {
        return CONFIG_PREFIX;
    }

    @Override
    public void store(UUID id, byte[] data) {
        secretDao.updateData(id, data);
    }

    @Override
    public void delete(UUID id) {
        // do nothing
    }

    @Override
    public byte[] get(UUID id) {
        return secretDao.getData(id);
    }
}
