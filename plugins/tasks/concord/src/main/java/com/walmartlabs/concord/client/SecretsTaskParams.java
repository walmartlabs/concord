package com.walmartlabs.concord.client;

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

import com.walmartlabs.concord.client2.SecretEntryV2;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.sdk.Constants;

import java.util.*;

public class SecretsTaskParams {

    public static SecretsTaskParams of(Variables variables) {
        SecretsTaskParams p = new SecretsTaskParams(variables);
        switch (p.action()) {
            case GETASSTRING: {
                return new AsStringParams(variables);
            }
            case CREATE: {
                return new CreateParams(variables);
            }
            case UPDATE: {
                return new UpdateParams(variables);
            }
            case DELETE: {
                return p;
            }
            default: {
                throw new IllegalArgumentException("Unsupported action type: " + p.action());
            }
        }
    }

    static final String ORG_KEY = "org";
    static final String SECRET_NAME_KEY = "name";
    static final String IGNORE_ERRORS_KEY = "ignoreErrors";

    protected final Variables variables;

    private SecretsTaskParams(Variables variables) {
        this.variables = variables;
    }

    public Action action() {
        String v = variables.getString(Keys.ACTION_KEY, Action.GETASSTRING.toString());
        try {
            return Action.valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown action: '" + v + "'. Available actions: " + Arrays.toString(Action.values()));
        }
    }

    public boolean ignoreErrors() {
        return variables.getBoolean(IGNORE_ERRORS_KEY, false);
    }

    public String orgName(String defaultOrg) {
        String org = variables.getString(ORG_KEY);
        if (org != null) {
            return org;
        }

        if (defaultOrg != null) {
            return defaultOrg;
        }

        throw new IllegalArgumentException("'" + ORG_KEY + "' is required");
    }

    public String secretName() {
        return variables.assertString(SECRET_NAME_KEY);
    }

    static class AsStringParams extends SecretsTaskParams {

        static String STORE_PASSWORD_KEY = "storePassword";

        private AsStringParams(Variables variables) {
            super(variables);
        }

        public String storePassword() {
            return variables.getString(STORE_PASSWORD_KEY);
        }
    }

    static class CreateParams extends SecretsTaskParams {

        static String DATA_KEY = "data";
        static final String PUBLIC_KEY = "public";
        static final String PRIVATE_KEY = "private";
        static final String USERNAME_KEY = "username";
        static final String PASSWORD_KEY = "password"; // NOSONAR
        static final String STORE_PASSWORD_KEY = "storePassword";
        static final String GENERATE_PASSWORD_KEY = "generatePassword"; // NOSONAR
        static final String VISIBILITY_KEY = "visibility";
        static final String PROJECT_NAME_KEY = "project";
        static final String PROJECT_NAMES_KEY = "projects";
        static final String PROJECT_IDS_KEY = "projectIds";

        private CreateParams(Variables variables) {
            super(variables);
        }

        public SecretEntryV2.TypeEnum secretType() {
            String type = variables.getString(Constants.Multipart.TYPE, SecretEntryV2.TypeEnum.DATA.toString());
            try {
                return SecretEntryV2.TypeEnum.valueOf(type.trim().toUpperCase());
            } catch (Exception e) {
                String message = String.format("Invalid argument '%s', allowed values are: 'data' (default), 'key_pair' and 'username_password'", type);
                throw new IllegalArgumentException(message);
            }
        }

        public Object data() {
            return variables.get(DATA_KEY, null, Object.class);
        }

        public String publicKey() {
            return variables.assertString(PUBLIC_KEY);
        }

        public String privateKey() {
            return variables.assertString(PRIVATE_KEY);
        }

        public String userName() {
            return variables.assertString(USERNAME_KEY);
        }

        public Object password() {
            return variables.assertString(PASSWORD_KEY);
        }

        public String storePassword() {
            return variables.getString(STORE_PASSWORD_KEY);
        }

        public Object generatePassword() {
            return variables.get(GENERATE_PASSWORD_KEY);
        }

        public Object visibility() {
            return variables.get(VISIBILITY_KEY);
        }

        public String projectName() {
            return variables.getString(PROJECT_NAME_KEY);
        }

        public List<String> projectNames() {
            return variables.getList(PROJECT_NAMES_KEY, null);
        }

        public List<UUID> projectIds() {
            return variables.getList(PROJECT_IDS_KEY, null);
        }
    }

    static class UpdateParams extends CreateParams {

        static final String NEW_STORE_PASSWORD_KEY = "newStorePassword"; // NOSONAR
        static final String CREATE_IF_MISSING_KEY = "createIfMissing";

        private UpdateParams(Variables variables) {
            super(variables);
        }

        public String newStorePassword() {
            return variables.getString(NEW_STORE_PASSWORD_KEY);
        }

        public boolean createIfMissing() {
            return variables.getBoolean(CREATE_IF_MISSING_KEY, false);
        }

    }

    public enum Action {
        GETASSTRING,
        CREATE,
        UPDATE,
        DELETE
    }
}
