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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.walmartlabs.concord.common.TruncBufferedReader;
import com.walmartlabs.concord.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.*;
import java.nio.file.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static com.walmartlabs.concord.plugins.ansible.ArgUtils.getListAsString;
import static com.walmartlabs.concord.sdk.MapUtils.*;

@Named("ansible2")
public class RunPlaybookTask2 implements Task {

    private static final Logger log = LoggerFactory.getLogger(RunPlaybookTask2.class);
    private static final Logger processLog = LoggerFactory.getLogger("processLog");

    private static final int SUCCESS_EXIT_CODE = 0;

    private final SecretService secretService;
    private final AnsibleAuthFactory ansibleAuthFactory;
    private final DockerService dockerService;

    @InjectVariable(Constants.Context.CONTEXT_KEY)
    Context context;

    @InjectVariable(Constants.Context.TX_ID_KEY)
    String txId;

    @Inject
    ApiConfiguration apiCfg;

    @InjectVariable("ansibleParams")
    private Map<String, Object> defaults;

    @Inject
    public RunPlaybookTask2(SecretService secretService,
                            AnsibleAuthFactory ansibleAuthFactory,
                            DockerService dockerService) {

        this.secretService = secretService;
        this.ansibleAuthFactory = ansibleAuthFactory;
        this.dockerService = dockerService;
    }

    public void run(String dockerImageName, Map<String, Object> args, String payloadPath) throws Exception {
        log.info("Using the docker image: {}", dockerImageName);

        Collection<String> extraHosts = DockerExtraHosts.getHosts(getMap(args, TaskParams.DOCKER_OPTS_KEY, null));
        log.info("Using extra /etc/hosts records: {}", extraHosts);

        run(args, payloadPath,
                new DockerPlaybookProcessBuilder(dockerService, context, dockerImageName)
                        .withForcePull(getBoolean(args, TaskParams.FORCE_PULL_KEY, true))
                        .withHosts(extraHosts));
    }

    public void run(Map<String, Object> args, String payloadPath) throws Exception {
        run(args, payloadPath, new PlaybookProcessBuilderImpl(payloadPath));
    }

    @Override
    public void execute(Context ctx) throws Exception {
        Map<String, Object> allArgs = new HashMap<>();

        Stream.of(TaskParams.values()).forEach(c ->
                addIfPresent(ctx, allArgs, c.getKey())
        );

        String payloadPath = ContextUtils.getString(ctx, Constants.Context.WORK_DIR_KEY);
        if (payloadPath == null) {
            payloadPath = ContextUtils.getString(ctx, TaskParams.WORK_DIR_KEY.getKey());
        }

        Path workDir = Paths.get(payloadPath);
        Map<String, Object> args = DeprecatedArgsProcessor.process(workDir, allArgs);

        String dockerImage = ContextUtils.getString(ctx, TaskParams.DOCKER_IMAGE_KEY.getKey());
        if (dockerImage != null) {
            run(dockerImage, args, payloadPath);
        } else {
            run(args, payloadPath);
        }
    }

    private void run(Map<String, Object> args, String payloadPath, PlaybookProcessBuilder processBuilder) throws Exception {
        String playbook = assertString(args, TaskParams.PLAYBOOK_KEY.getKey());
        log.info("Using a playbook: {}", playbook);

        boolean debug = getBoolean(args, TaskParams.DEBUG_KEY.getKey(), false);

        Path workDir = Paths.get(payloadPath);
        Path tmpDir = createTmpDir(workDir);

        TaskContext taskContext = new TaskContext(context, defaults, workDir, tmpDir, debug, args);

        AnsibleEnv env = new AnsibleEnv(context, apiCfg, debug)
                .parse(args);

        AnsibleConfig cfg = new AnsibleConfig(workDir, tmpDir, debug)
                .parse(args)
                .enrich(env);

        AnsibleCallbacks.process(taskContext, cfg);
        AnsibleLibs.process(taskContext, env);
        AnsibleLookup.process(taskContext, cfg);

        PlaybookArgsBuilder b = new PlaybookArgsBuilder(playbook, workDir, tmpDir);

        AnsibleInventory.process(taskContext, b);

        AnsibleVaultPassword.process(taskContext, b);

        AnsibleRoles.process(taskContext, cfg);

        GroupVarsProcessor groupVarsProcessor = new GroupVarsProcessor(secretService, context);
        groupVarsProcessor.process(txId, args, workDir);

        OutVarsProcessor outVarsProcessor = new OutVarsProcessor();
        outVarsProcessor.prepare(args, env.get(), workDir, tmpDir);

        AnsibleAuth auth = ansibleAuthFactory.create(taskContext)
                .enrich(env)
                .enrich(b);

        cfg.write();
        env.write();

        boolean checkMode = getBoolean(args, TaskParams.CHECK_KEY.getKey(), false);
        if (checkMode) {
            log.warn("Running in the check mode. No changes will be made.");
        }

        boolean syntaxCheck = getBoolean(args, TaskParams.SYNTAX_CHECK_KEY.getKey(), false);
        if (syntaxCheck) {
            log.warn("Running in the syntax check mode. No changes will be made.");
        }

        try {
            Path attachmentsPath = workDir.relativize(workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME));

            b = b.withAttachmentsDir(attachmentsPath.toString())
                    .withTags(getListAsString(args, TaskParams.TAGS_KEY))
                    .withSkipTags(getListAsString(args, TaskParams.SKIP_TAGS_KEY))
                    .withExtraVars(getMap(args, TaskParams.EXTRA_VARS_KEY.getKey(), null))
                    .withExtraVarsFiles(getList(args, TaskParams.EXTRA_VARS_FILES_KEY.getKey(), null))
                    .withLimit(getLimit(args, playbook))
                    .withVerboseLevel(getVerboseLevel(args))
                    .withCheck(checkMode)
                    .withSyntaxCheck(syntaxCheck)
                    .withEnv(env.get());

            auth.prepare();

            Process p = processBuilder
                    .withDebug(debug)
                    .build(b.buildArgs(), b.buildEnv());

            BufferedReader reader = new TruncBufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                processLog.info("ANSIBLE: {}", line);
            }

            int code = p.waitFor();
            log.debug("execution -> done, code {}", code);

            updateAnsibleStats(workDir, code);
            outVarsProcessor.process(context);

            if (code != SUCCESS_EXIT_CODE) {
                saveRetryFile(args, workDir);
                log.warn("Playbook is finished with code {}", code);
                throw new IllegalStateException("Process finished with exit code " + code);
            }
        } finally {
            auth.postProcess();
            groupVarsProcessor.postProcess();
            outVarsProcessor.postProcess();
        }
    }

    private String getLimit(Map<String, Object> args, String playbook) {
        boolean retry = getBoolean(args, TaskParams.RETRY_KEY.getKey(), false);
        if (retry) {
            return "@" + getNameWithoutExtension(playbook) + ".retry";
        }

        String limit = getString(args, TaskParams.LIMIT_KEY.getKey());
        if (limit != null) {
            return limit;
        }

        return null;
    }

    private static Path createTmpDir(Path workDir) throws IOException {
        Path p = workDir.resolve(Constants.Files.CONCORD_TMP_DIR_NAME);
        Files.createDirectories(p);
        return Files.createTempDirectory(p, "ansible");
    }

    private static void addIfPresent(Context src, Map<String, Object> dst, String k) {
        Object v = src.getVariable(k);
        if (v != null) {
            dst.put(k, v);
        }
    }

    private static void saveRetryFile(Map<String, Object> args, Path workDir) throws IOException {
        boolean saveRetryFiles = getBoolean(args, TaskParams.SAVE_RETRY_FILE.getKey(), false);
        if (!saveRetryFiles) {
            return;
        }

        String playbookName = getString(args, TaskParams.PLAYBOOK_KEY.getKey());
        String retryFile = getNameWithoutExtension(playbookName + ".retry");
        if (retryFile == null) {
            return;
        }

        Path src = workDir.resolve(retryFile);
        if (!Files.exists(src)) {
            log.warn("Retry file not found: {}", src);
            return;
        }

        Path dst = workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(TaskParams.LAST_RETRY_FILE.getKey());
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        log.info("The retry file was saved as: {}", workDir.relativize(dst));
    }

    private static int getVerboseLevel(Map<String, Object> args) {
        return getNumber(args, TaskParams.VERBOSE_LEVEL_KEY.getKey(), 0).intValue();
    }

    private static String getNameWithoutExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        return (i == -1) ? fileName : fileName.substring(0, i);
    }

    @SuppressWarnings("unchecked")
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
}
