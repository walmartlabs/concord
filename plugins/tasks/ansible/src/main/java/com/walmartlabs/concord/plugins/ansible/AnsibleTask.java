package com.walmartlabs.concord.plugins.ansible;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.plugins.ansible.secrets.AnsibleSecretService;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.sdk.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.plugins.ansible.ArgUtils.getListAsString;
import static com.walmartlabs.concord.sdk.MapUtils.*;

public class AnsibleTask {

    private static final Logger log = LoggerFactory.getLogger(AnsibleTask.class);

    private static final int SUCCESS_EXIT_CODE = 0;

    private final ApiClient apiClient;
    private final AnsibleAuthFactory ansibleAuthFactory;
    private final AnsibleSecretService secretService;

    public AnsibleTask(ApiClient apiClient,
                       AnsibleAuthFactory ansibleAuthFactory,
                       AnsibleSecretService secretService) {

        this.apiClient = apiClient;
        this.ansibleAuthFactory = ansibleAuthFactory;
        this.secretService = secretService;
    }

    public TaskResult.SimpleResult run(AnsibleContext context,
                                       PlaybookProcessRunner playbookProcessRunner) throws Exception {

        String playbook = assertString(context.args(), TaskParams.PLAYBOOK_KEY.getKey());
        log.info("Using a playbook: {}", playbook);

        AnsibleEnv env = new AnsibleEnv(context)
                .parse(context.args());

        AnsibleConfig cfg = new AnsibleConfig(context)
                .parse(context.args())
                .enrich(env);

        AnsibleCallbacks callbacks = AnsibleCallbacks.process(context, cfg)
                .startEventSender(context.instanceId(), new ProcessEventsApi(apiClient))
                .enrich(env);

        AnsibleLibs.process(context, env);
        AnsibleLookup.process(context, cfg);

        PlaybookScriptBuilder b = new PlaybookScriptBuilder(context, playbook);

        AnsibleInventory.process(context, b);

        AnsibleVaultId.process(context, b);

        AnsibleRoles.process(context, cfg);

        GroupVarsProcessor groupVarsProcessor = new GroupVarsProcessor(secretService);
        groupVarsProcessor.process(context, playbook);

        OutVarsProcessor outVarsProcessor = new OutVarsProcessor();
        outVarsProcessor.prepare(context, env.get());

        AnsibleAuth auth = ansibleAuthFactory.create(context)
                .enrich(env, context)
                .enrich(b);

        cfg.write();
        env.write();

        boolean checkMode = getBoolean(context.args(), TaskParams.CHECK_KEY.getKey(), false);
        if (checkMode) {
            log.warn("Running in the check mode. No changes will be made.");
        }

        boolean syntaxCheck = getBoolean(context.args(), TaskParams.SYNTAX_CHECK_KEY.getKey(), false);
        if (syntaxCheck) {
            log.warn("Running in the syntax check mode. No changes will be made.");
        }

        Virtualenv virtualenv = Virtualenv.create(context);

        boolean skipCheckBinary = getBoolean(context.args(), TaskParams.SKIP_CHECK_BINARY.getKey(), false);

        try {
            Path workDir = context.workDir();
            Path attachmentsPath = workDir.relativize(workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME));

            b = b.withAttachmentsDir(attachmentsPath.toString())
                    .withDebug(context.debug())
                    .withTags(getListAsString(context.args(), TaskParams.TAGS_KEY))
                    .withSkipTags(getListAsString(context.args(), TaskParams.SKIP_TAGS_KEY))
                    .withExtraVars(getMap(context.args(), TaskParams.EXTRA_VARS_KEY.getKey(), null))
                    .withExtraVarsFiles(getList(context.args(), TaskParams.EXTRA_VARS_FILES_KEY.getKey(), null))
                    .withLimit(getLimit(context.args(), playbook))
                    .withVerboseLevel(getVerboseLevel(context.args()))
                    .withCheck(checkMode)
                    .withSyntaxCheck(syntaxCheck)
                    .withEnv(env.get())
                    .withVirtualenv(virtualenv)
                    .withSkipCheckBinary(skipCheckBinary);

            auth.prepare();

            int code = playbookProcessRunner
                    .withDebug(context.debug())
                    .run(b.buildArgs(), b.buildEnv());

            log.debug("execution -> done, code {}", code);

            updateAnsibleStats(workDir, code);
            updateAnsibleStatsV2(workDir, code);

            Map<String, Object> result = outVarsProcessor.process();

            boolean success = code == SUCCESS_EXIT_CODE;
            if (!success) {
                saveRetryFile(context.args(), workDir);
                log.warn("Playbook is finished with code {}", code);
            }

            return TaskResult.of(success)
                    .values(result)
                    .value("exitCode", code);
        } finally {
            callbacks.stopEventSender();

            auth.postProcess();
            groupVarsProcessor.postProcess();
            outVarsProcessor.postProcess();

            virtualenv.destroy();
        }
    }

    private static String getLimit(Map<String, Object> args, String playbook) {
        boolean debug = getBoolean(args, TaskParams.DEBUG_KEY.getKey(), false);

        boolean retry = getBoolean(args, TaskParams.RETRY_KEY.getKey(), false);
        if (retry) {
            String s = "@" + getNameWithoutExtension(playbook) + ".retry";
            if (debug) {
                log.info("Using a limit file: {}", s);
            }
            return s;
        }

        String limit = getListAsString(args, TaskParams.LIMIT_KEY);
        if (limit != null) {
            if (debug) {
                log.info("Using the limit value: {}", limit);
            }
            return limit;
        }

        return null;
    }

    private static String getNameWithoutExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        return (i == -1) ? fileName : fileName.substring(0, i);
    }

    private static int getVerboseLevel(Map<String, Object> args) {
        return getNumber(args, TaskParams.VERBOSE_LEVEL_KEY.getKey(), 0).intValue();
    }

    @SuppressWarnings("unchecked")
    @Deprecated
    private static void updateAnsibleStats(Path workDir, int code) throws IOException {
        Path p = workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(TaskParams.STATS_FILE_NAME.getKey());
        if (!Files.exists(p)) {
            return;
        }

        ObjectMapper om = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        Map<String, Object> m = new HashMap<>();
        try (InputStream in = Files.newInputStream(p)) {
            Map<String, Object> mm = om.readValue(in, Map.class);
            m.putAll(mm);
        }

        m.put(TaskParams.EXIT_CODE_KEY.getKey(), code);

        try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            om.writeValue(out, m);
        }
    }

    @SuppressWarnings("unchecked")
    private static void updateAnsibleStatsV2(Path workDir, int code) throws IOException {
        Path p = workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve("ansible_stats_v2.json");
        if (!Files.exists(p)) {
            return;
        }

        ObjectMapper om = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        List<Map<String, Object>> stats = new ArrayList<>();
        try (InputStream in = Files.newInputStream(p)) {
            stats.addAll(om.readValue(in, List.class));
        }

        if (stats.isEmpty()) {
            return;
        }

        Map<String, Object> currentStats = new HashMap<>(stats.get(stats.size() - 1));
        currentStats.put(TaskParams.EXIT_CODE_KEY.getKey(), code);
        stats.set(stats.size() - 1, currentStats);

        try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            om.writeValue(out, stats);
        }
    }

    private static void saveRetryFile(Map<String, Object> args, Path workDir) throws IOException {
        boolean saveRetryFiles = getBoolean(args, TaskParams.SAVE_RETRY_FILE.getKey(), false);
        if (!saveRetryFiles) {
            return;
        }

        String playbookName = getString(args, TaskParams.PLAYBOOK_KEY.getKey());
        String retryFile = getNameWithoutExtension(playbookName) + ".retry";

        Path src = workDir.resolve(retryFile);
        if (!Files.exists(src)) {
            log.warn("Retry file not found: {}", src);
            return;
        }

        Path dst = workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(src.getFileName());
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        log.info("The retry file was saved as: {}", workDir.relativize(dst));
    }
}
