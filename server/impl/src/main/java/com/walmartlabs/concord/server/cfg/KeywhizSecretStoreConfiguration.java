package com.walmartlabs.concord.server.cfg;

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

import com.google.common.base.Strings;
import com.walmartlabs.ollie.config.Config;
import org.eclipse.sisu.Nullable;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class KeywhizSecretStoreConfiguration {

    @Inject
    @Config("secretStore.keywhiz.enabled")
    private boolean enabled;

    @Inject
    @Config("secretStore.keywhiz.url")
    private String url;

    @Inject
    @Config("secretStore.keywhiz.trustStore")
    private String trustStore;

    @Inject
    @Config("secretStore.keywhiz.trustStorePassword")
    @Nullable
    private String trustStorePassword;

    @Inject
    @Config("secretStore.keywhiz.keyStore")
    private String keyStore;

    @Inject
    @Config("secretStore.keywhiz.keyStorePassword")
    @Nullable
    private String keyStorePassword;

    @Inject
    @Config("secretStore.keywhiz.connectTimeout")
    private int connectTimeout;

    @Inject
    @Config("secretStore.keywhiz.soTimeout")
    private int soTimeout;

    @Inject
    @Config("secretStore.keywhiz.connectionRequestTimeout")
    private int connectionRequestTimeout;

    @Inject
    public KeywhizSecretStoreConfiguration() {
        if (!enabled) {
            return;
        }

        if (Strings.isNullOrEmpty(trustStorePassword)) {
            throw new IllegalArgumentException("secretStore.keywhiz.trustStorePassword is required");
        }

        if (Strings.isNullOrEmpty(keyStorePassword)) {
            throw new IllegalArgumentException("secretStore.keywhiz.keyStorePassword");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTrustStore() {
        return trustStore;
    }

    public void setTrustStore(String trustStore) {
        this.trustStore = trustStore;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public String getKeyStore() {
        return keyStore;
    }

    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    public void setSoTimeout(int soTimeout) {
        this.soTimeout = soTimeout;
    }

    public int getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    public void setConnectionRequestTimeout(int connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
    }
}
