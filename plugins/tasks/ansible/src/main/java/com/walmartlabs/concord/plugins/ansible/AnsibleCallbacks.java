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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static com.walmartlabs.concord.plugins.ansible.ArgUtils.getBoolean;

public class AnsibleCallbacks {

    private static final Logger log = LoggerFactory.getLogger(AnsibleCallbacks.class);

    private static final String CALLBACK_LOCATION = "/com/walmartlabs/concord/plugins/ansible/callback";
    private static final String CALLBACK_PLUGINS_DIR = "_callbacks";
    private static final String[] CALLBACKS = new String[]{
            "concord_events.py", "concord_trace.py", "concord_protectdata.py",
            "concord_strategy_patch.py", "concord_task_executor_patch.py", "concord_out_vars.py"};

    private final Path tmpDir;

    private boolean disabled = false;

    public AnsibleCallbacks(Path tmpDir) {
        this.tmpDir = tmpDir;
    }

    public static void process(Path tmpDir, Map<String, Object> args, AnsibleConfig config) {
        new AnsibleCallbacks(tmpDir).parse(args).enrich(config).write();
    }

    public AnsibleCallbacks parse(Map<String, Object> args) {
        disabled = getBoolean(args, TaskParams.DISABLE_CONCORD_CALLBACKS_KEY, false);
        return this;
    }

    public void write() {
        if (disabled) {
            return;
        }

        try {
            Resources.copy(CALLBACK_LOCATION, CALLBACKS, getDir());
        } catch (IOException e) {
            log.error("Error while adding Concord callback plugins: {}", e.getMessage(), e);
            throw new RuntimeException("Error while adding Concord callback plugins: " + e.getMessage());
        }
    }

    public AnsibleCallbacks enrich(AnsibleConfig config) {
        if (disabled) {
            return this;
        }

        config.getDefaults()
                .prependPath("callback_plugins", CALLBACK_PLUGINS_DIR)
                .put("stdout_callback", "concord_protectdata");

        return this;
    }

    private Path getDir() {
        return tmpDir.resolve(CALLBACK_PLUGINS_DIR);
    }
}
