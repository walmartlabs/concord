package com.walmartlabs.concord.server.cfg;

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

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.text.NumberFormat;

import com.walmartlabs.concord.server.org.secret.store.SecretStorePropertyManager;

@Named
@Singleton
public class KeywhizConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KeywhizConfiguration.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String trustStore;
    private final char[] trustStorePassword;
    private final String keyStore;
    private final char[] keyStorePassword;
    private final int connectTimeout;
    private final int soTimeout;
    private final int connectionRequestTimeout;

    @Inject
    public KeywhizConfiguration(SecretStorePropertyManager secretStorePropertyManager) throws IOException {
        this.enabled = Boolean.parseBoolean(Strings.emptyToNull(secretStorePropertyManager.getProperty(KeywhizConstant.PROP_KEYWHIZ_ENABLED)));
        this.baseUrl = getUrl(secretStorePropertyManager, KeywhizConstant.PROP_KEYWHIZ_URL);
        this.trustStore = Strings.emptyToNull(secretStorePropertyManager.getProperty(KeywhizConstant.PROP_KEYWHIZ_TRUST_STORE));
        this.trustStorePassword = getPassword(secretStorePropertyManager, KeywhizConstant.PROP_KEYWHIZ_TRUST_STORE_PASSWORD);
        this.keyStore = Strings.emptyToNull(secretStorePropertyManager.getProperty(KeywhizConstant.PROP_KEYWHIZ_KEY_STORE));
        this.keyStorePassword = getPassword(secretStorePropertyManager, KeywhizConstant.PROP_KEYWHIZ_KEY_STORE_PASSWORD);
        this.connectTimeout = getInteger(secretStorePropertyManager, KeywhizConstant.PROP_KEYWHIZ_CONNECT_TIMEOUT, 5_000);
        this.soTimeout = getInteger(secretStorePropertyManager, KeywhizConstant.PROP_KEYWHIZ_SO_TIMEOUT, 5_000);
        this.connectionRequestTimeout = getInteger(secretStorePropertyManager, KeywhizConstant.PROP_KEYWHIZ_CONNECTION_REQUEST_TIMEOUT, 5_000);

        log.info("init -> keywhiz support: {}", enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getTrustStore() {
        return trustStore;
    }

    public char[] getTrustStorePassword() {
        return trustStorePassword;
    }

    public String getKeyStore() {
        return keyStore;
    }

    public char[] getKeyStorePassword() {
        return keyStorePassword;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    public int getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    private boolean getEnabled(SecretStorePropertyManager secretStoreConfiguration, String name) {
        String enabledString = secretStoreConfiguration.getProperty(name);
        return Boolean.valueOf(enabledString);
    }

    private String getUrl(SecretStorePropertyManager secretStoreConfiguration, String name) {
        String url = secretStoreConfiguration.getProperty(name);
        if (url == null) {
            return null;
        }

        if (url.endsWith("/")) {
            return url.substring(url.length() - 1);
        }
        return url;
    }

    private char[] getPassword(SecretStorePropertyManager secretStoreConfiguration, String name) {
        String prop = Strings.emptyToNull(secretStoreConfiguration.getProperty(name));
        if (prop == null) {
            return null;
        }
        return prop.toCharArray();
    }

    private static Number getNumber(SecretStorePropertyManager secretStoreConfiguration, String name) {
        String value = secretStoreConfiguration.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return NumberFormat.getInstance().parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid value '" + name + "' format: " + value, e);
        }
    }

    private static int getInteger(SecretStorePropertyManager secretStoreConfiguration, String name, int defaultValue) {
        Number value = getNumber(secretStoreConfiguration, name);
        if (value != null) {
            return value.intValue();
        }

        return defaultValue;
    }

    private static final class KeywhizConstant {

        public static final String PROP_KEYWHIZ_ENABLED = "keywhiz.enabled";
        public static final String PROP_KEYWHIZ_URL = "keywhiz.url";
        public static final String PROP_KEYWHIZ_TRUST_STORE = "keywhiz.trustStore";
        public static final String PROP_KEYWHIZ_TRUST_STORE_PASSWORD = "keywhiz.trustStorePassword";
        public static final String PROP_KEYWHIZ_KEY_STORE = "keywhiz.keyStore";
        public static final String PROP_KEYWHIZ_KEY_STORE_PASSWORD = "keywhiz.keyStorePassword";
        public static final String PROP_KEYWHIZ_CONNECT_TIMEOUT = "keywhiz.connectTimeout";
        public static final String PROP_KEYWHIZ_SO_TIMEOUT = "keywhiz.soTimeout";
        public static final String PROP_KEYWHIZ_CONNECTION_REQUEST_TIMEOUT = "keywhiz.connectionRequestTimeout";

        private KeywhizConstant() {
        }
    }
}
