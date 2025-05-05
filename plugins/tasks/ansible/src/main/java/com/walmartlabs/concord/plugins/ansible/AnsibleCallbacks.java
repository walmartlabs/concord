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

import com.walmartlabs.concord.client2.ProcessEventsApi;
import com.walmartlabs.concord.common.TimeProvider;
import com.walmartlabs.concord.sdk.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AnsibleCallbacks {

    public static AnsibleCallbacks process(AnsibleContext ctx, AnsibleConfig config) {
        return new AnsibleCallbacks(ctx.workDir(), ctx.tmpDir(), ctx.debug())
                .parse(ctx.argsWithDefaults())
                .enrich(config)
                .write();
    }

    private static final Logger log = LoggerFactory.getLogger(AnsibleCallbacks.class);

    private static final String CALLBACK_LOCATION = "/com/walmartlabs/concord/plugins/ansible/callback";
    private static final String CALLBACK_PLUGINS_DIR = "_callbacks";

    private static final String[] STATS_CALLBACKS = new String[]{
            "concord_trace.py"
    };

    private static final String[] EVENTS_CALLBACKS = new String[]{
            "concord_events.py",
            "concord_strategy_patch.py"
    };
    private static final String[] OUTVARS_CALLBACKS = new String[]{
            "concord_out_vars.py"
    };
    private static final String[] POLICY_CALLBACKS = new String[]{
            "concord_task_executor_patch.py"
    };
    private static final String[] LOG_FILTERING_CALLBACKS = new String[]{
            "concord_protectdata.py"
    };
    private static final String[] MODULE_DEFAULTS_CALLBACKS = new String[]{
            "concord_default_module_args.py"
    };

    private final boolean debug;
    private final Path workDir;
    private final Path tmpDir;

    private boolean disabled = false;
    private boolean policyEnabled = false;
    private boolean logFilteringEnabled = false;
    private boolean eventsEnabled = false;
    private boolean statsEnabled = false;
    private boolean outVarsEnabled = false;
    private boolean moduleDefaultsEnabled = false;

    private Path eventsFile;
    private EventSender eventSender;
    private Future<?> eventSenderFuture;

    public AnsibleCallbacks(Path workDir, Path tmpDir, boolean debug) {
        this.debug = debug;
        this.workDir = workDir;
        this.tmpDir = tmpDir;
    }

    public AnsibleCallbacks parse(Map<String, Object> args) {
        this.disabled = MapUtils.getBoolean(args, TaskParams.DISABLE_CONCORD_CALLBACKS_KEY, false);
        this.policyEnabled = MapUtils.getBoolean(args, TaskParams.ENABLE_POLICY, false);
        this.logFilteringEnabled = MapUtils.getBoolean(args, TaskParams.ENABLE_LOG_FILTERING, false);

        this.eventsEnabled = MapUtils.getBoolean(args, TaskParams.ENABLE_EVENTS, true);
        this.statsEnabled = MapUtils.getBoolean(args, TaskParams.ENABLE_STATS, true);
        this.outVarsEnabled = MapUtils.getBoolean(args, TaskParams.ENABLE_OUT_VARS, true);
        this.moduleDefaultsEnabled = MapUtils.getBoolean(args, TaskParams.ENABLE_MODULE_DEFAULTS, true);

        return this;
    }

    public AnsibleCallbacks write() {
        if (disabled) {
            return this;
        }

        try {
            if (policyEnabled) {
                Resources.copy(CALLBACK_LOCATION, POLICY_CALLBACKS, getDir());
            }

            if (logFilteringEnabled) {
                Resources.copy(CALLBACK_LOCATION, LOG_FILTERING_CALLBACKS, getDir());
            }

            if (eventsEnabled) {
                Resources.copy(CALLBACK_LOCATION, EVENTS_CALLBACKS, getDir());
            }

            if (statsEnabled) {
                Resources.copy(CALLBACK_LOCATION, STATS_CALLBACKS, getDir());
            }

            if (outVarsEnabled) {
                Resources.copy(CALLBACK_LOCATION, OUTVARS_CALLBACKS, getDir());
            }

            if (moduleDefaultsEnabled) {
                Resources.copy(CALLBACK_LOCATION, MODULE_DEFAULTS_CALLBACKS, getDir());
            }
        } catch (IOException e) {
            log.error("Error while adding Concord callback plugins: {}", e.getMessage(), e);
            throw new RuntimeException("Error while adding Concord callback plugins: " + e.getMessage());
        }

        return this;
    }

    public AnsibleCallbacks enrich(AnsibleConfig config) {
        if (disabled) {
            return this;
        }

        ConfigSection defaults = config.getDefaults()
                .prependPath("callback_plugins", CALLBACK_PLUGINS_DIR);

        if (logFilteringEnabled) {
            defaults.put("stdout_callback", "concord_protectdata");
        }

        return this;
    }

    public AnsibleCallbacks startEventSender(UUID instanceId, ProcessEventsApi eventsApi, TimeProvider timeProvider) throws IOException {
        if (disabled || !eventsEnabled) {
            return this;
        }

        this.eventsFile = Files.createTempFile(tmpDir, "events", ".log");
        this.eventSender = new EventSender(debug, instanceId, eventsFile, eventsApi, timeProvider);
        this.eventSenderFuture = eventSender.start();

        return this;
    }

    public void stopEventSender() {
        if (eventSender == null) {
            return;
        }

        this.eventSender.stop();

        try {
            this.eventSenderFuture.get(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Error while stopping the event sending thread", e);
        }

        this.eventSender = null;
    }

    public AnsibleCallbacks enrich(AnsibleEnv env) {
        if (disabled) {
            return this;
        }

        if (eventsFile != null) {
            // must be a relative path to support Ansible containers
            env.put("CONCORD_ANSIBLE_EVENTS_FILE", workDir.relativize(eventsFile).toString());
        }

        return this;
    }

    private Path getDir() {
        return tmpDir.resolve(CALLBACK_PLUGINS_DIR);
    }
}
