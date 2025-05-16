package com.walmartlabs.concord.plugins.crypto;

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

import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.util.Arrays;
import java.util.Collections;

public class TaskParams {

    private static final String ACTION_KEY = "action";
    private static final String SECRET_NAME_KEY = "secretName";
    private static final String GENERATE_PASSWORD_KEY = "generatePassword";
    private static final String STORE_PASSWORD_KEY = "storePassword";
    private static final String VISIBILITY_KEY = "visibility";
    private static final String ORG_KEY = "org";
    private static final String PROJECT_KEY = "project";

    static final String KEY_PAIR_KEY = "keyPair";
    static final String USERNAME_PASSWORD_KEY = "usernamePassword";
    static final String DATA_KEY = "data";
    static final String STRING_VALUE_KEY = "stringValue";

    private final Variables variables;

    public TaskParams(Variables variables) {
        this.variables = variables;
    }

    public Action action() {
        String action = variables.assertString(ACTION_KEY);
        try {
            return Action.valueOf(action.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown action: '" + action + "'. Available actions: " + Arrays.toString(Action.values()));
        }
    }

    public String secretName() {
        return variables.assertString(SECRET_NAME_KEY);
    }

    public boolean generatePassword() {
        return variables.getBoolean(GENERATE_PASSWORD_KEY, false);
    }

    public String storePassword() {
        return variables.getString(STORE_PASSWORD_KEY);
    }

    public String visibility() {
        return variables.getString(VISIBILITY_KEY);
    }

    public String orgOrDefault(String defaultValue) {
        String org = variables.getString(ORG_KEY, defaultValue);
        if (org == null) {
            throw new IllegalArgumentException("An organization name is required.");
        }
        return org;
    }

    public String project() {
        return variables.getString(PROJECT_KEY);
    }

    public KeyPair keyPair() {
        if (variables.has(KEY_PAIR_KEY)) {
            return new KeyPair(new MapBackedVariables(variables.getMap(KEY_PAIR_KEY, Collections.emptyMap())));
        }
        return null;
    }

    public String data() {
        return variables.getString(DATA_KEY);
    }

    public String stringValue() {
        return variables.getString(STRING_VALUE_KEY);
    }

    public UsernamePassword usernamePassword() {
        if (variables.has(USERNAME_PASSWORD_KEY)) {
            return new UsernamePassword(new MapBackedVariables(variables.getMap(USERNAME_PASSWORD_KEY, Collections.emptyMap())));
        }
        return null;
    }

    public static class KeyPair {

        private static final String PUBLIC_KEY = "public";
        private static final String PRIVATE_KEY = "private";

        private final Variables variables;

        public KeyPair(Variables variables) {
            this.variables = variables;
        }

        public String publicKey() {
            return variables.assertString(PUBLIC_KEY);
        }

        public String privateKey() {
            return variables.assertString(PRIVATE_KEY);
        }
    }

    public static class UsernamePassword {

        private static final String USERNAME_KEY = "username";
        private static final String PASSWORD_KEY = "password";

        private final Variables variables;

        public UsernamePassword(Variables variables) {
            this.variables = variables;
        }

        public String username() {
            return variables.assertString(USERNAME_KEY);
        }

        public String password() {
            return variables.assertString(PASSWORD_KEY);
        }
    }

    public enum Action {
        CREATE
    }
}
