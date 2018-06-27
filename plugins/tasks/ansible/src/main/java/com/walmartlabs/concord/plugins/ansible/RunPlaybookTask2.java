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
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.TruncBufferedReader;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.project.yaml.converter.DockerOptionsConverter;
import com.walmartlabs.concord.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

import static com.walmartlabs.concord.project.InternalConstants.Context.EVENT_CORRELATION_KEY;

@Named("ansible2")
public class RunPlaybookTask2 implements Task {

    private static final Logger log = LoggerFactory.getLogger(RunPlaybookTask2.class);

    private static final int SUCCESS_EXIT_CODE = 0;
    private static final String PYTHON_LIB_DIR = "_python_lib";
    private static final String CALLBACK_PLUGINS_DIR = "_callbacks";
    private static final String LOOKUP_PLUGINS_DIR = "_lookups";
    private static final String STRATEGY_PLUGINS_DIR = "_strategy";

    private final SecretService secretService;

    @InjectVariable(Constants.Context.CONTEXT_KEY)
    Context context;

    @InjectVariable(Constants.Context.TX_ID_KEY)
    String txId;

    @Inject
    ApiConfiguration apiCfg;

    @Inject
    public RunPlaybookTask2(SecretService secretService) {
        this.secretService = secretService;
    }

    public void run(String dockerImageName, Map<String, Object> args, String payloadPath) throws Exception {
        log.info("Using the docker image: {}", dockerImageName);

        List<Map.Entry<String, String>> dockerOpts = DockerOptionsConverter.convert(ArgUtils.getMap(args, AnsibleConstants.DOCKER_OPTS_KEY));
        log.info("Using the docker options: {}", dockerOpts);

        run(args, payloadPath,
                new DockerPlaybookProcessBuilder(txId, dockerImageName, payloadPath)
                        .withForcePull(ArgUtils.getBoolean(args, AnsibleConstants.FORCE_PULL_KEY, true))
                        .withDockerOptions(dockerOpts));
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    public void run(Map<String, Object> args, String payloadPath) throws Exception {
        run(args, payloadPath, new PlaybookProcessBuilderImpl(payloadPath));
    }

    @Override
    public void execute(Context ctx) throws Exception {
        Map<String, Object> args = new HashMap<>();

        addIfPresent(ctx, args, AnsibleConstants.CONFIG_KEY);
        addIfPresent(ctx, args, AnsibleConstants.DEBUG_KEY);
        addIfPresent(ctx, args, AnsibleConstants.DISABLE_CONCORD_CALLBACKS_KEY);
        addIfPresent(ctx, args, AnsibleConstants.DOCKER_OPTS_KEY);
        addIfPresent(ctx, args, AnsibleConstants.DYNAMIC_INVENTORY_FILE_KEY);
        addIfPresent(ctx, args, AnsibleConstants.EXTRA_ENV_KEY);
        addIfPresent(ctx, args, AnsibleConstants.EXTRA_VARS_KEY);
        addIfPresent(ctx, args, AnsibleConstants.FORCE_PULL_KEY);
        addIfPresent(ctx, args, AnsibleConstants.GROUP_VARS_KEY);
        addIfPresent(ctx, args, AnsibleConstants.INVENTORY_FILE_KEY);
        addIfPresent(ctx, args, AnsibleConstants.INVENTORY_KEY);
        addIfPresent(ctx, args, AnsibleConstants.LIMIT_KEY);
        addIfPresent(ctx, args, AnsibleConstants.PLAYBOOK_KEY);
        addIfPresent(ctx, args, AnsibleConstants.PRIVATE_KEY_FILE_KEY);
        addIfPresent(ctx, args, AnsibleConstants.RETRY_KEY);
        addIfPresent(ctx, args, AnsibleConstants.SAVE_RETRY_FILE);
        addIfPresent(ctx, args, AnsibleConstants.SKIP_TAGS_KEY);
        addIfPresent(ctx, args, AnsibleConstants.TAGS_KEY);
        addIfPresent(ctx, args, AnsibleConstants.USER_KEY);
        addIfPresent(ctx, args, AnsibleConstants.VAULT_PASSWORD_FILE_KEY);
        addIfPresent(ctx, args, AnsibleConstants.VAULT_PASSWORD_KEY);
        addIfPresent(ctx, args, AnsibleConstants.VERBOSE_LEVEL_KEY);
        addIfPresent(ctx, args, AnsibleConstants.OUT_VARS_KEY);

        String payloadPath = (String) ctx.getVariable(Constants.Context.WORK_DIR_KEY);
        if (payloadPath == null) {
            payloadPath = (String) ctx.getVariable(AnsibleConstants.WORK_DIR_KEY);
        }

        String dockerImage = (String) ctx.getVariable(AnsibleConstants.DOCKER_IMAGE_KEY);
        if (dockerImage != null) {
            run(dockerImage, args, payloadPath);
        } else {
            run(args, payloadPath);
        }
    }

    private void run(Map<String, Object> args, String payloadPath, PlaybookProcessBuilder processBuilder) throws Exception {
        String playbook = ArgUtils.getString(args, AnsibleConstants.PLAYBOOK_KEY);
        if (playbook == null || playbook.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing '" + AnsibleConstants.PLAYBOOK_KEY + "' parameter");
        }
        log.info("Using a playbook: {}", playbook);

        boolean debug = ArgUtils.getBoolean(args, AnsibleConstants.DEBUG_KEY, false);

        Path workDir = Paths.get(payloadPath);
        Path tmpDir = createTmpDir(workDir);

        boolean disableConcordCallbacks = ArgUtils.getBoolean(args, AnsibleConstants.DISABLE_CONCORD_CALLBACKS_KEY, false);
        Path cfgFile = workDir.relativize(getCfgFile(args, workDir, tmpDir, debug, disableConcordCallbacks));

        Path inventoryPath = workDir.relativize(getInventoryPath(args, workDir, tmpDir));

        Path attachmentsPath = workDir.relativize(workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME));

        Path vaultPasswordPath = getVaultPasswordFilePath(args, workDir, tmpDir);
        if (vaultPasswordPath != null) {
            vaultPasswordPath = workDir.relativize(vaultPasswordPath);
        }

        Path privateKeyPath = getPrivateKeyPath(args, workDir);
        if (privateKeyPath != null) {
            privateKeyPath = workDir.relativize(privateKeyPath);
        }

        if (disableConcordCallbacks) {
            log.warn("Concord-specific Ansible callbacks are disabled.");
        } else {
            processCallback(workDir, tmpDir);
        }

        processLookups(workDir, tmpDir);
        processStrategy(tmpDir);

        final Map<String, String> env = addExtraEnv(defaultEnv(workDir), args);
        UUID eventCorrelationId = (UUID) context.getVariable(EVENT_CORRELATION_KEY);
        if (eventCorrelationId != null) {
            env.put("CONCORD_EVENT_CORRELATION_ID", eventCorrelationId.toString());
        }

        GroupVarsProcessor groupVarsProcessor = new GroupVarsProcessor(secretService, context);
        groupVarsProcessor.process(txId, args, workDir);

        OutVarsProcessor outVarsProcessor = new OutVarsProcessor();
        outVarsProcessor.prepare(args, env, workDir, tmpDir);

        try {
            PlaybookArgsBuilder b = new PlaybookArgsBuilder(playbook, inventoryPath.toString(), workDir, tmpDir)
                    .withAttachmentsDir(toString(attachmentsPath))
                    .withCfgFile(toString(cfgFile))
                    .withPrivateKey(toString(privateKeyPath))
                    .withVaultPasswordFile(toString(vaultPasswordPath))
                    .withUser(ArgUtils.getString(args, AnsibleConstants.USER_KEY))
                    .withTags(ArgUtils.getListAsString(args, AnsibleConstants.TAGS_KEY))
                    .withSkipTags(ArgUtils.getListAsString(args, AnsibleConstants.SKIP_TAGS_KEY))
                    .withExtraVars(ArgUtils.getMap(args, AnsibleConstants.EXTRA_VARS_KEY))
                    .withLimit(getLimit(args, playbook))
                    .withVerboseLevel(getVerboseLevel(args))
                    .withEnv(env);

            Process p = processBuilder
                    .withDebug(debug)
                    .build(b.buildArgs(), b.buildEnv());

            BufferedReader reader = new TruncBufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("ANSIBLE: {}", line);
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
        boolean retry = ArgUtils.getBoolean(args, AnsibleConstants.RETRY_KEY, false);
        if (retry) {
            return "@" + getNameWithoutExtension(playbook) + ".retry";
        }

        String limit = ArgUtils.getString(args, AnsibleConstants.LIMIT_KEY);
        if (limit != null) {
            return limit;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> defaultEnv(Path ws) {
        final Map<String, String> env = new HashMap<>();
        env.put("PYTHONPATH", PYTHON_LIB_DIR);
        env.put("CONCORD_INSTANCE_ID", (String) context.getVariable(Constants.Context.TX_ID_KEY));
        env.put("CONCORD_BASE_URL", apiCfg.getBaseUrl());

        String t = apiCfg.getSessionToken(context);
        env.put("CONCORD_SESSION_TOKEN", t != null ? t : "none");

        env.put("CONCORD_POLICY", ws.resolve(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME)
                .resolve(InternalConstants.Files.POLICY_FILE_NAME).toString());

        Map<String, Object> projectInfo = (Map<String, Object>) context.getVariable(Constants.Request.PROJECT_INFO_KEY);
        String orgName = projectInfo != null ? (String) projectInfo.get("orgName") : null;
        if (orgName != null) {
            env.put("CONCORD_CURRENT_ORG_NAME", orgName);
        }

        return env;
    }

    private void processCallback(Path workDir, Path tmpDir) throws IOException {
        Path libDir = workDir.resolve(PYTHON_LIB_DIR);
        Files.createDirectories(libDir);

        copyResourceToFile("/com/walmartlabs/concord/plugins/ansible/lib/task_policy.py", libDir.resolve("task_policy.py"));

        Path callbackDir = tmpDir.resolve(CALLBACK_PLUGINS_DIR);
        Files.createDirectories(callbackDir);
        copyResourceToFile("/com/walmartlabs/concord/plugins/ansible/callback/concord_events.py", callbackDir.resolve("concord_events.py"));
        copyResourceToFile("/com/walmartlabs/concord/plugins/ansible/callback/concord_trace.py", callbackDir.resolve("concord_trace.py"));
        copyResourceToFile("/com/walmartlabs/concord/plugins/ansible/callback/concord_protectdata.py", callbackDir.resolve("concord_protectdata.py"));
        copyResourceToFile("/com/walmartlabs/concord/plugins/ansible/callback/concord_strategy_enforce.py", callbackDir.resolve("concord_strategy_enforce.py"));
        copyResourceToFile("/com/walmartlabs/concord/plugins/ansible/callback/concord_out_vars.py", callbackDir.resolve("concord_out_vars.py"));
    }

    private void processStrategy(Path tmpDir) throws IOException {
        Path strategyDir = tmpDir.resolve(STRATEGY_PLUGINS_DIR);
        Files.createDirectories(strategyDir);
        copyResourceToFile("/com/walmartlabs/concord/plugins/ansible/strategy/concord_free.py", strategyDir.resolve("concord_free.py"));
        copyResourceToFile("/com/walmartlabs/concord/plugins/ansible/strategy/concord_linear.py", strategyDir.resolve("concord_linear.py"));
    }

    private void processLookups(Path workDir, Path tmpDir) throws IOException {
        Path lookupDir = tmpDir.resolve(LOOKUP_PLUGINS_DIR);
        Files.createDirectories(lookupDir);
        copyResourceToFile("/com/walmartlabs/concord/plugins/ansible/lookup/concord_inventory.py", lookupDir.resolve("concord_inventory.py"));
        copyResourceToFile("/com/walmartlabs/concord/plugins/ansible/lookup/concord_public_key_secret.py", lookupDir.resolve("concord_public_key_secret.py"));
        copyResourceToFile("/com/walmartlabs/concord/plugins/ansible/lookup/concord_data_secret.py", lookupDir.resolve("concord_data_secret.py"));
        // For backward compatibility
        copyResourceToFile("/com/walmartlabs/concord/plugins/ansible/lookup/concord_secret.py", lookupDir.resolve("concord_secret.py"));
    }

    private static void copyResourceToFile(String resourceName, Path dest) throws IOException {
        try (InputStream is = RunPlaybookTask2.class.getResourceAsStream(resourceName)) {
            Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path createTmpDir(Path workDir) throws IOException {
        Path p = workDir.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME);
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

    @SuppressWarnings("unchecked")
    private static Path getInventoryPath(Map<String, Object> args, Path workDir, Path tmpDir) throws IOException {
        // try an "inline" inventory
        Object v = args.get(AnsibleConstants.INVENTORY_KEY);
        if (v instanceof Map) {
            Path p = createInventoryFile(tmpDir, (Map<String, Object>) v);
            updateScriptPermissions(p);
            log.info("Using an inline inventory");
            return p;
        }

        // try a static inventory file
        v = args.get(AnsibleConstants.INVENTORY_FILE_KEY);
        if (v != null) {
            Path p = workDir.resolve(v.toString());
            if (!Files.exists(p) || !Files.isRegularFile(p)) {
                throw new IllegalArgumentException("File not found: " + v);
            }
            log.info("Using a static inventory file: {}", p);
            return p;
        }

        // try an "old school" inventory file
        Path p = workDir.resolve(AnsibleConstants.INVENTORY_FILE_NAME);
        if (Files.exists(p)) {
            log.info("Using a static inventory file uploaded separately: {}", p);
            return p;
        }

        // try a dynamic inventory script
        v = args.get(AnsibleConstants.DYNAMIC_INVENTORY_FILE_KEY);
        if (v != null) {
            p = workDir.resolve(v.toString());
            updateScriptPermissions(p);
            log.info("Using a dynamic inventory script: {}", p);
            return p;
        }

        // try an "old school" dynamic inventory script
        p = workDir.resolve(AnsibleConstants.DYNAMIC_INVENTORY_FILE_NAME);
        if (Files.exists(p)) {
            updateScriptPermissions(p);
            log.info("Using a dynamic inventory script uploaded separately: {}", p);
            return p;
        }

        // we can't continue without an inventory
        throw new IOException("Inventory is not defined");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> makeAnsibleCfg(Map<String, Object> userCfg, boolean disableConcordCallbacks) {
        Map<String, Object> defaults = makeDefaults();

        Map<String, Object> m = new HashMap<>();
        m.put("defaults", defaults);
        m.put("ssh_connection", makeSshConnCfg());

        if (userCfg != null) {
            m = ConfigurationUtils.deepMerge(m, userCfg);

            // prepend plugin paths with default values
            Map<String, Object> userDefaults = ArgUtils.assertMap(userCfg, "defaults");
            if (userDefaults != null) {
                Map<String, Object> mergedDefaults = (Map<String, Object>) m.get("defaults");

                mergedDefaults.put("callback_plugins", mergePaths(defaults, userDefaults, "callback_plugins"));
                mergedDefaults.put("lookup_plugins", mergePaths(defaults, userDefaults, "lookup_plugins"));
            }
        }

        // enforce some of the defaults
        defaults = (Map<String, Object>) m.get("defaults");

        if (!disableConcordCallbacks) {
            // enable the log filtering plugin
            defaults.put("stdout_callback", "concord_protectdata");
        }

        // enforce the policy engine strategy
        defaults.put("strategy_plugins", STRATEGY_PLUGINS_DIR);

        return m;
    }

    private static Map<String, Object> makeDefaults() {
        Map<String, Object> m = new HashMap<>();

        // disable puppet / chef fact gathering, significant speed/performance increase - usually unneeded
        // may eventually need !hardware for AIX/HPUX or set at runtime, Ansible 2.4 fixes many broken facts
        m.put("gather_subset", "!facter,!ohai");

        // disable ssl host key checking by default
        m.put("host_key_checking", false);

        //SSH timeout, default is 10 seconds and too slow for stores
        m.put("timeout", "120");

        // use a shorter path to store temporary files
        m.put("remote_tmp", "/tmp/ansible/$USER");

        // add plugins path
        m.put("callback_plugins", CALLBACK_PLUGINS_DIR);
        m.put("lookup_plugins", LOOKUP_PLUGINS_DIR);

        return m;
    }

    private static String mergePaths(Map<String, Object> defaults, Map<String, Object> user, String key) {
        String a = (String) defaults.get(key);

        if (user == null) {
            return a;
        }

        String b = (String) user.get(key);
        if (b == null) {
            return a;
        }

        return a + ":" + b;
    }

    private static Map<String, Object> makeSshConnCfg() {
        Map<String, Object> m = new HashMap<>();

        // use a shorter control_path to prevent path length errors
        m.put("control_path", "%(directory)s/%%h-%%p-%%r");

        // Default pipelining to True for better overall performance, compatibility
        m.put("pipelining", "True");

        return m;
    }

    @SuppressWarnings("unchecked")
    private static Path getCfgFile(Map<String, Object> args, Path workDir, Path tmpDir,
                                   boolean debug, boolean disableConcordCallbacks) throws IOException {

        String s = (String) args.get(AnsibleConstants.CONFIG_FILE_KEY);
        if (s != null) {
            Path provided = workDir.resolve(s);
            if (Files.exists(provided)) {
                log.info("Using the provided configuration file: {}", provided);
                return provided;
            }
        }

        Map<String, Object> cfg = (Map<String, Object>) args.get(AnsibleConstants.CONFIG_KEY);
        return createCfgFile(tmpDir, makeAnsibleCfg(cfg, disableConcordCallbacks), debug);
    }

    @SuppressWarnings("unchecked")
    private static Path createCfgFile(Path tmpDir, Map<String, Object> cfg, boolean debug) throws IOException {
        StringBuilder b = new StringBuilder();

        for (Map.Entry<String, Object> c : cfg.entrySet()) {
            String k = c.getKey();
            Object v = c.getValue();
            if (!(v instanceof Map)) {
                throw new IllegalArgumentException("Invalid configuration. Expected a JSON object: " + k + ", got: " + v);
            }

            b = addCfgSection(b, k, (Map<String, Object>) v);
        }

        if (debug) {
            log.info("Using the configuration: \n{}", b);
        }

        Path tmpFile = tmpDir.resolve("ansible.cfg");
        Files.write(tmpFile, b.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);

        log.debug("createCfgFile -> done, created {}", tmpFile);
        return tmpFile;
    }

    private static StringBuilder addCfgSection(StringBuilder b, String name, Map<String, Object> m) {
        if (m == null || m.isEmpty()) {
            return b;
        }

        b.append("[").append(name).append("]\n");
        for (Map.Entry<String, Object> e : m.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }

            b.append(e.getKey()).append(" = ").append(v).append("\n");
        }
        return b;
    }

    private static Path getPath(Map<String, Object> args, String key, Path workDir) throws IOException {
        Path p = null;

        Object v = args.get(key);
        if (v instanceof String) {
            p = workDir.resolve((String) v);
        } else if (v instanceof Path) {
            p = workDir.resolve((Path) v);
        } else if (v != null) {
            throw new IllegalArgumentException("'" + key + "' should be either a relative path: " + v);
        }

        if (p != null && !Files.exists(p)) {
            throw new IllegalArgumentException("File not found: " + workDir.relativize(p));
        }

        return p;
    }

    @SuppressWarnings("unchecked")
    private Path getPrivateKeyPath(Map<String, Object> args, Path workDir) throws Exception {
        Path p;

        Object o = args.get(AnsibleConstants.PRIVATE_KEY_FILE_KEY);
        if (o instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) o;
            String name = (String) m.get("secretName");
            if (name == null) {
                throw new IllegalArgumentException("Secret name is required to export a private key");
            }

            String password = (String) m.get("password");
            String orgName = (String) m.get("org");

            Map<String, String> keyPair = secretService.exportKeyAsFile(context, txId, workDir.toAbsolutePath().toString(), orgName, name, password);
            p = Paths.get(keyPair.get("private"));
        } else {
            p = getPath(args, AnsibleConstants.PRIVATE_KEY_FILE_KEY, workDir);
        }

        if (p == null) {
            p = workDir.resolve(AnsibleConstants.PRIVATE_KEY_FILE_NAME);
            if (!Files.exists(p)) {
                return null;
            }
        }

        if (!Files.exists(p)) {
            throw new IllegalArgumentException("Private key file not found: " + p);
        }

        log.info("Using the private key: {}", p);

        // ensure that the key has proper permissions (chmod 600)
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        Files.setPosixFilePermissions(p, perms);

        return p.toAbsolutePath();
    }

    @SuppressWarnings("deprecation")
    private static Path getVaultPasswordFilePath(Map<String, Object> args, Path workDir, Path tmpDir) throws IOException {
        // check if there is a path to a vault password file
        Path p = getPath(args, AnsibleConstants.VAULT_PASSWORD_FILE_KEY, workDir);
        if (p != null) {
            if (isAScript(p)) {
                updateScriptPermissions(p);
            }

            log.info("Using the provided vault password file: {}", workDir.relativize(p));
            return p;
        }

        // try an "inline" password
        Object v = args.get(AnsibleConstants.VAULT_PASSWORD_KEY);
        if (v instanceof String) {
            p = tmpDir.resolve("vault_password");
            Files.write(p, ((String) v).getBytes(), StandardOpenOption.CREATE);
            log.info("Using the provided vault password.");
            return p;
        } else if (v != null) {
            throw new IllegalArgumentException("Invalid '" + AnsibleConstants.VAULT_PASSWORD_KEY + "' type: " + v);
        }

        p = workDir.resolve(AnsibleConstants.VAULT_PASSWORD_FILE_PATH);
        if (!Files.exists(p)) {
            return null;
        }

        return p;
    }

    private static void saveRetryFile(Map<String, Object> args, Path workDir) throws IOException {
        boolean saveRetryFiles = ArgUtils.getBoolean(args, AnsibleConstants.SAVE_RETRY_FILE, false);
        if (!saveRetryFiles) {
            return;
        }

        String playbookName = ArgUtils.getString(args, AnsibleConstants.PLAYBOOK_KEY);
        String retryFile = getNameWithoutExtension(playbookName + ".retry");
        if (retryFile == null) {
            return;
        }

        Path src = workDir.resolve(retryFile);
        if (!Files.exists(src)) {
            log.warn("Retry file not found: {}", src);
            return;
        }

        Path dst = workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME).resolve(AnsibleConstants.LAST_RETRY_FILE);
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        log.info("The retry file was saved as: {}", workDir.relativize(dst));
    }

    private static Path createInventoryFile(Path tmpDir, Map<String, Object> m) throws IOException {
        Path p = tmpDir.resolve("inventory.sh");

        try (BufferedWriter w = Files.newBufferedWriter(p, StandardOpenOption.CREATE)) {
            w.write("#!/bin/sh");
            w.newLine();
            w.write("cat << \"EOF\"");
            w.newLine();

            ObjectMapper om = new ObjectMapper();
            String s = om.writeValueAsString(m);
            w.write(s);
            w.newLine();

            w.write("EOF");
            w.newLine();
        }

        return p;
    }

    private static boolean isAScript(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".sh") || n.endsWith(".py");
    }

    private static void updateScriptPermissions(Path p) throws IOException {
        // ensure that the file has the executable bit set
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        perms.add(PosixFilePermission.OWNER_WRITE);
        Files.setPosixFilePermissions(p, perms);
    }

    private static int getVerboseLevel(Map<String, Object> args) {
        return ArgUtils.getInt(args, AnsibleConstants.VERBOSE_LEVEL_KEY, 0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> addExtraEnv(Map<String, String> env, Map<String, Object> m) {
        Map<String, String> extraEnv = (Map<String, String>) m.get(AnsibleConstants.EXTRA_ENV_KEY);
        if (extraEnv == null || extraEnv.isEmpty()) {
            return env;
        }

        Map<String, String> result = new HashMap<>(env);
        result.putAll(extraEnv);

        return result;
    }

    private static String getNameWithoutExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        return (i == -1) ? fileName : fileName.substring(0, i);
    }

    @SuppressWarnings("unchecked")
    private static void updateAnsibleStats(Path workDir, int code) throws IOException {
        Path p = workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME).resolve(AnsibleConstants.STATS_FILE_NAME);
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

        m.put(AnsibleConstants.EXIT_CODE_KEY, code);

        try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            om.writeValue(out, m);
        }
    }
}
