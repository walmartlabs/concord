package com.walmartlabs.concord.plugins.ansible;

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


import com.walmartlabs.concord.sdk.HasKey;

public enum TaskParams implements HasKey {

    AUTH("auth"),

    CHECK_KEY("check"),

    CONFIG_FILE_KEY("configFile"),

    CONFIG_KEY("config"),

    DEBUG_KEY("debug"),

    DISABLE_CONCORD_CALLBACKS_KEY("disableConcordCallbacks"),

    DOCKER_IMAGE_KEY("dockerImage"),

    DOCKER_OPTS_KEY("dockerOpts"),

    DOCKER_PULL_RETRY_COUNT("pullRetryCount"),

    DOCKER_PULL_RETRY_INTERVAL("pullRetryInterval"),

    DYNAMIC_INVENTORY_FILE_KEY("dynamicInventoryFile"),

    @Deprecated
    DYNAMIC_INVENTORY_FILE_NAME("_dynamicInventory"),

    ENABLE_POLICY("enablePolicy"),

    ENABLE_LOG_FILTERING("enableLogFiltering"),

    ENABLE_EVENTS("enableEvents"),

    ENABLE_STATS("enableStats"),

    ENABLE_OUT_VARS("enableOutsVars"),

    ENABLE_MODULE_DEFAULTS("enableModuleDefaults"),

    EXIT_CODE_KEY("exitCode"),

    EXTRA_ENV_KEY("extraEnv"),

    EXTRA_VARS_KEY("extraVars"),

    EXTRA_VARS_FILES_KEY("extraVarsFiles"),

    FORCE_PULL_KEY("forcePull"),

    GROUP_VARS_KEY("groupVars"),

    INVENTORY_FILE_KEY("inventoryFile"),

    @Deprecated
    INVENTORY_FILE_NAME("_inventory"),

    INVENTORY_KEY("inventory"),

    @Deprecated
    LAST_RETRY_FILE("ansible.retry"),

    LIMIT_KEY("limit"),

    OUT_VARS_KEY("outVars"),

    PASSWORD("password"),

    PLAYBOOK_KEY("playbook"),

    /**
     * @deprecated use {@link #AUTH}
     */
    @Deprecated
    PRIVATE_KEY_FILE_KEY("privateKey"),

    /**
     * @deprecated use {@link #AUTH}
     */
    @Deprecated
    PRIVATE_KEY_FILE_NAME("_privateKey"),

    RETRY_KEY("retry"),

    ROLES_KEY("roles"),

    SAVE_RETRY_FILE("saveRetryFile"),

    SKIP_TAGS_KEY("skipTags"),

    // TODO not really a TaskParam
    STATS_FILE_NAME("ansible_stats.json"),

    SYNTAX_CHECK_KEY("syntaxCheck"),

    TAGS_KEY("tags"),

    /**
     * @deprecated use {@link #AUTH}
     */
    @Deprecated
    USER_KEY("user"),

    VAULT_PASSWORD_FILE_KEY("vaultPasswordFile"),

    /**
     * @deprecated set the path explicitly using
     * {@link #VAULT_PASSWORD_FILE_KEY} input parameter
     */
    @Deprecated
    VAULT_PASSWORD_FILE_PATH("_vaultPassword"),

    VAULT_PASSWORD_KEY("vaultPassword"),

    VERBOSE_LEVEL_KEY("verbose"),

    VIRTUALENV_KEY("virtualenv"),

    SKIP_CHECK_BINARY("skipCheckBinary"),

    WORK_DIR_KEY("workDir");

    private final String key;

    TaskParams(String key) {
        this.key = key;
    }

    @Override
    public String getKey() {
        return key;
    }
}
