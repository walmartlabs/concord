package com.walmartlabs.concord.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

public final class AnsibleConstants {

    public static final String CONFIG_KEY = "config";

    public static final String CONFIG_FILE_KEY = "configFile";

    public static final String DEBUG_KEY = "debug";

    public static final String DOCKER_IMAGE_KEY = "dockerImage";

    public static final String FORCE_PULL_KEY = "forcePull";

    public static final String DYNAMIC_INVENTORY_FILE_KEY = "dynamicInventoryFile";

    public static final String DYNAMIC_INVENTORY_FILE_NAME = "_dynamicInventory";

    public static final String EXIT_CODE_KEY = "exitCode";

    public static final String EXTRA_VARS_KEY = "extraVars";

    public static final String EXTRA_ENV_KEY = "extraEnv";

    public static final String INVENTORY_FILE_KEY = "inventoryFile";

    public static final String INVENTORY_FILE_NAME = "_inventory";

    public static final String INVENTORY_KEY = "inventory";

    public static final String PLAYBOOK_KEY = "playbook";

    public static final String PRIVATE_KEY_FILE_KEY = "privateKey";

    public static final String PRIVATE_KEY_FILE_NAME = "_privateKey";

    public static final String STATS_FILE_NAME = "ansible_stats.json";

    public static final String TAGS_KEY = "tags";

    public static final String SKIP_TAGS_KEY = "skipTags";

    public static final String USER_KEY = "user";

    public static final String RETRY_KEY = "retry";

    public static final String LIMIT_KEY = "limit";

    public static final String SAVE_RETRY_FILE = "saveRetryFile";

    public static final String LAST_RETRY_FILE = "ansible.retry";

    /**
     * @deprecated set the path explicitly using
     * {@link #VAULT_PASSWORD_FILE_KEY} input parameter
     */
    @Deprecated
    public static final String VAULT_PASSWORD_FILE_PATH = "_vaultPassword";

    public static final String VAULT_PASSWORD_KEY = "vaultPassword";

    public static final String VAULT_PASSWORD_FILE_KEY = "vaultPasswordFile";

    public static final String WORK_DIR_KEY = "workDir";

    public static final String VERBOSE_LEVEL_KEY = "verbose";

    public static final String DOCKER_OPTS_KEY = "dockerOpts";

    public static final String GROUP_VARS_KEY = "groupVars";

    public static final String DISABLE_CONCORD_CALLBACKS_KEY = "disableConcordCallbacks";

    public static final String OUT_VARS_KEY = "outVars";

    private AnsibleConstants() {
    }
}
