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
import com.walmartlabs.concord.project.yaml.converter.DockerOptionsConverter;
import com.walmartlabs.concord.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.walmartlabs.concord.plugins.ansible.ArgUtils.*;

@Named("ansible2")
public class RunPlaybookTask2 implements Task {

    private static final Logger log = LoggerFactory.getLogger(RunPlaybookTask2.class);
    private static final Logger processLog = LoggerFactory.getLogger("processLog");

    private static final int SUCCESS_EXIT_CODE = 0;

    private final SecretService secretService;

    @InjectVariable(Constants.Context.CONTEXT_KEY)
    Context context;

    @InjectVariable(Constants.Context.TX_ID_KEY)
    String txId;

    @Inject
    ApiConfiguration apiCfg;

    @InjectVariable("ansibleParams")
    private Map<String, Object> defaults;

    @Inject
    public RunPlaybookTask2(SecretService secretService) {
        this.secretService = secretService;
    }

    public void run(String dockerImageName, Map<String, Object> args, String payloadPath) throws Exception {
        log.info("Using the docker image: {}", dockerImageName);

        List<Map.Entry<String, String>> dockerOpts = DockerOptionsConverter.convert(getMap(args, TaskParams.DOCKER_OPTS_KEY));
        log.info("Using the docker options: {}", dockerOpts);

        run(args, payloadPath,
                new DockerPlaybookProcessBuilder(txId, dockerImageName, payloadPath)
                        .withForcePull(getBoolean(args, TaskParams.FORCE_PULL_KEY, true))
                        .withDockerOptions(dockerOpts));
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    public void run(Map<String, Object> args, String payloadPath) throws Exception {
        run(args, payloadPath, new PlaybookProcessBuilderImpl(payloadPath));
    }

    @Override
    public void execute(Context ctx) throws Exception {
        Map<String, Object> args = new HashMap<>();

        Stream.of(TaskParams.values()).forEach(c ->
            addIfPresent(ctx, args, c.getKey())
        );

        String payloadPath = ContextUtils.getString(ctx, Constants.Context.WORK_DIR_KEY);
        if (payloadPath == null) {
            payloadPath = ContextUtils.getString(ctx, TaskParams.WORK_DIR_KEY.getKey());
        }

        String dockerImage = ContextUtils.getString(ctx, TaskParams.DOCKER_IMAGE_KEY.getKey());
        if (dockerImage != null) {
            run(dockerImage, args, payloadPath);
        } else {
            run(args, payloadPath);
        }
    }

    private void run(Map<String, Object> args, String payloadPath, PlaybookProcessBuilder processBuilder) throws Exception {
        String playbook = getString(args, TaskParams.PLAYBOOK_KEY);
        if (playbook == null || playbook.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing '" + TaskParams.PLAYBOOK_KEY + "' parameter");
        }
        log.info("Using a playbook: {}", playbook);

        boolean debug = getBoolean(args, TaskParams.DEBUG_KEY, false);

        Path workDir = Paths.get(payloadPath);
        Path tmpDir = createTmpDir(workDir);

        AnsibleEnv env = new AnsibleEnv(context, apiCfg, debug)
                .parse(args);

        AnsibleConfig cfg = new AnsibleConfig(workDir, tmpDir, debug)
                .parse(args)
                .enrichEnv(env);

        AnsibleCallbacks.process(tmpDir, args, cfg);
        AnsibleLibs.process(workDir, tmpDir, env);
        AnsibleLookup.process(tmpDir, cfg);
        AnsibleStrategy.process(tmpDir, cfg);

        Path inventoryPath = new AnsibleInventory(workDir, tmpDir, debug).write(args);

        Path attachmentsPath = workDir.relativize(workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME));

        Path vaultPasswordPath = AnsibleVaultPassword.process(workDir, tmpDir, args);

        Path privateKeyPath = AnsiblePrivateKey.process(secretService, context, workDir, args);

        GroupVarsProcessor groupVarsProcessor = new GroupVarsProcessor(secretService, context);
        groupVarsProcessor.process(txId, args, workDir);

        OutVarsProcessor outVarsProcessor = new OutVarsProcessor();
        outVarsProcessor.prepare(args, env.get(), workDir, tmpDir);

        AnsibleRoles.process(workDir, tmpDir, defaults, args, cfg, debug);

        cfg.write();
        env.write();

        try {
            PlaybookArgsBuilder b = new PlaybookArgsBuilder(playbook, inventoryPath.toString(), workDir, tmpDir)
                    .withAttachmentsDir(toString(attachmentsPath))
                    .withPrivateKey(toString(privateKeyPath))
                    .withVaultPasswordFile(toString(vaultPasswordPath))
                    .withUser(getString(args, TaskParams.USER_KEY))
                    .withTags(getListAsString(args, TaskParams.TAGS_KEY))
                    .withSkipTags(getListAsString(args, TaskParams.SKIP_TAGS_KEY))
                    .withExtraVars(getMap(args, TaskParams.EXTRA_VARS_KEY))
                    .withLimit(getLimit(args, playbook))
                    .withVerboseLevel(getVerboseLevel(args))
                    .withCheck(getCheck(args, playbook))
                    .withEnv(env.get());

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
            groupVarsProcessor.postProcess();
            outVarsProcessor.postProcess();
        }
    }

    private String getLimit(Map<String, Object> args, String playbook) {
        boolean retry = ArgUtils.getBoolean(args, TaskParams.RETRY_KEY, false);
        if (retry) {
            return "@" + getNameWithoutExtension(playbook) + ".retry";
        }

        String limit = ArgUtils.getString(args, TaskParams.LIMIT_KEY);
        if (limit != null) {
            return limit;
        }

        return null;
    }

    private boolean getCheck(Map<String, Object> args, String playbook) {
        return ArgUtils.getBoolean(args, TaskParams.CHECK_KEY, false);
    }

    private static Path createTmpDir(Path workDir) throws IOException {
        Path p = workDir.resolve(Constants.Files.CONCORD_TMP_DIR_NAME);
        Files.createDirectories(p);
        return Files.createTempDirectory(p, "ansible");
    }

    private static String toString(Path p) {
        if (p == null) {
            return null;
        }
        return p.toString();
    }

    private static void addIfPresent(Context src, Map<String, Object> dst, String k) {
        Object v = src.getVariable(k);
        if (v != null) {
            dst.put(k, v);
        }
    }

    private static void saveRetryFile(Map<String, Object> args, Path workDir) throws IOException {
        boolean saveRetryFiles = getBoolean(args, TaskParams.SAVE_RETRY_FILE, false);
        if (!saveRetryFiles) {
            return;
        }

        String playbookName = getString(args, TaskParams.PLAYBOOK_KEY);
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
        return getInt(args, TaskParams.VERBOSE_LEVEL_KEY, 0);
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
