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

import com.walmartlabs.concord.client.ProcessEventsApi;
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

import static com.walmartlabs.concord.sdk.MapUtils.getBoolean;

public class AnsibleCallbacks {

    public static AnsibleCallbacks process(TaskContext ctx, AnsibleConfig config) {
        return new AnsibleCallbacks(ctx.isDebug(), ctx.getTmpDir())
                .parse(ctx.getArgs())
                .enrich(config)
                .write();
    }

    private static final Logger log = LoggerFactory.getLogger(AnsibleCallbacks.class);

    private static final String CALLBACK_LOCATION = "/com/walmartlabs/concord/plugins/ansible/callback";
    private static final String CALLBACK_PLUGINS_DIR = "_callbacks";
    private static final String[] CALLBACKS = new String[]{
            "concord_events.py", "concord_trace.py", "concord_protectdata.py",
            "concord_strategy_patch.py", "concord_task_executor_patch.py", "concord_out_vars.py"};

    private final boolean debug;
    private final Path tmpDir;

    private boolean disabled = false;

    private Path eventsFile;
    private EventSender eventSender;
    private Future<?> eventSenderFuture;

    public AnsibleCallbacks(boolean debug, Path tmpDir) {
        this.debug = debug;
        this.tmpDir = tmpDir;
    }

    public AnsibleCallbacks parse(Map<String, Object> args) {
        disabled = getBoolean(args, TaskParams.DISABLE_CONCORD_CALLBACKS_KEY, false);
        return this;
    }

    public AnsibleCallbacks write() {
        if (disabled) {
            return this;
        }

        try {
            Resources.copy(CALLBACK_LOCATION, CALLBACKS, getDir());
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

        config.getDefaults()
                .prependPath("callback_plugins", CALLBACK_PLUGINS_DIR)
                .put("stdout_callback", "concord_protectdata");

        return this;
    }

    public AnsibleCallbacks startEventSender(UUID instanceId, ProcessEventsApi eventsApi) throws IOException {
        if (disabled) {
            return this;
        }

        this.eventsFile = Files.createTempFile(tmpDir, "events", ".log");
        this.eventSender = new EventSender(debug, instanceId, eventsFile, eventsApi);
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
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.warn("Error while stopping the event sending thread", e);
        }

        this.eventSender = null;
    }

    public AnsibleCallbacks enrich(AnsibleEnv env) {
        if (disabled) {
            return this;
        }

        if (eventsFile != null) {
            env.put("CONCORD_ANSIBLE_EVENTS_FILE", eventsFile.toAbsolutePath().toString());
        }

        return this;
    }

    private Path getDir() {
        return tmpDir.resolve(CALLBACK_PLUGINS_DIR);
    }
}
