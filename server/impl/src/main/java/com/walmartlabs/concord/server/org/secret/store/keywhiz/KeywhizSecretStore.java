package com.walmartlabs.concord.server.org.secretStore.keywhiz;

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

import com.google.common.base.Throwables;
import com.walmartlabs.concord.server.cfg.KeywhizSecretStoreConfiguration;
import com.walmartlabs.concord.server.org.secret.SecretStoreType;
import com.walmartlabs.concord.server.org.secret.store.SecretStore;
import com.walmartlabs.concord.server.org.secretStore.keywhiz.KeywhizClient;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.UUID;

@Named("keywhizSecretStore")
@Singleton
public class KeywhizSecretStore implements SecretStore {

    private static final String DESCRIPTION = "Keywhiz";

    private final boolean enabled;
    private final KeywhizClient client;

    @Inject
    public KeywhizSecretStore(KeywhizSecretStoreConfiguration cfg, KeywhizClient client) {
        this.enabled = cfg.isEnabled();
        this.client = client;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void store(UUID id, byte[] content) {
        try {
            this.client.createSecret(id.toString(), content);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void delete(UUID id) {
        try {
            this.client.deleteSecret(id.toString());
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public byte[] get(UUID id) {
        try {
            return this.client.getSecret(id.toString());
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public SecretStoreType getType() {
        return SecretStoreType.KEYWHIZ;
    }
}
