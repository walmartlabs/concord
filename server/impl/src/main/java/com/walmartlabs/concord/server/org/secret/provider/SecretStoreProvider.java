package com.walmartlabs.concord.server.org.secret.provider;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.server.api.org.secret.SecretStoreType;
import com.walmartlabs.concord.server.org.secret.store.SecretStore;
import com.walmartlabs.concord.server.org.secret.store.SecretStorePropertyManager;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Collection;

@Named
public class SecretStoreProvider {

    public static final String DEFAULT_STORE_KEY = "default.store";

    private final Collection<SecretStore> stores;
    private final SecretStorePropertyManager propertyManager;

    @Inject
    public SecretStoreProvider(Collection<SecretStore> stores, SecretStorePropertyManager propertyManager) {
        this.stores = stores;
        this.propertyManager = propertyManager;
    }

    public SecretStore getSecretStore(SecretStoreType secretSourceType) {
        for (SecretStore secretStore : stores) {
            if (secretSourceType == secretStore.getType()) {
                if (isEnabled(secretStore)) {
                    return secretStore;
                }

                throw new IllegalArgumentException("Secret store of type " + secretStore.getType() + " is not enabled!");
            }
        }

        throw new IllegalArgumentException("Secret store of type " + secretSourceType + " is not found!");
    }

    public Collection<SecretStore> getActiveSecretStores() {
        Collection<SecretStore> activeStores = new ArrayList<>();

        for (SecretStore secretStore : stores) {
            if (isEnabled(secretStore)) {
                activeStores.add(secretStore);
            }
        }

        return activeStores;
    }

    public SecretStoreType getDefaultStoreType() {
        String defaultStore = propertyManager.getProperty(DEFAULT_STORE_KEY);
        if (defaultStore == null) {
            Collection<SecretStore> activeSecretStores = getActiveSecretStores();
            if (activeSecretStores.size() == 1) {
                return activeSecretStores.iterator().next().getType();
            }

            throw new ValidationErrorsException(DEFAULT_STORE_KEY + " configuration property is missing");
        }

        try {
            return SecretStoreType.valueOf(defaultStore.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationErrorsException("Unsupported secret store type: " + defaultStore);
        }
    }

    private boolean isEnabled(SecretStore secretStore) {
        String isEnabledString = secretStore.getConfigurationPrefix() + ".enabled";
        return Boolean.valueOf(propertyManager.getProperty(isEnabledString));
    }
}
