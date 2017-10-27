package com.walmartlabs.concord.plugins.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.walmartlabs.concord.common.ConfigurationUtils;
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

@Named("ansible2")
public class RunPlaybookTask2 implements Task {

    private static final Logger log = LoggerFactory.getLogger(RunPlaybookTask2.class);

    private static final int SUCCESS_EXIT_CODE = 0;
    private static final String CALLBACK_DIR = "_callbacks";
    private static final String PYTHON_LIB_DIR = "_python_lib";
    private static final String LOOKUP_DIR = "lookup";

    private final RpcConfiguration rpcCfg;
    private final SecretStore secretStore;

    @InjectVariable(Constants.Context.CONTEXT_KEY)
    Context context;

    @InjectVariable(Constants.Context.TX_ID_KEY)
    String txId;

    @Inject
    ApiConfiguration apiCfg;

    @Inject
    public RunPlaybookTask2(RpcConfiguration rpcCfg, SecretStore secretStore) {
        this.rpcCfg = rpcCfg;
        this.secretStore = secretStore;
    }

    @SuppressWarnings("unchecked")
    private void run(Map<String, Object> args, String payloadPath, PlaybookProcessBuilderFactory pb) throws Exception {
        boolean debug = getBoolean(args, AnsibleConstants.DEBUG_KEY, false);

        Path workDir = Paths.get(payloadPath);
        Path tmpDir = Files.createTempDirectory(workDir, "ansible");

        String playbook = getString(args, AnsibleConstants.PLAYBOOK_KEY);
        if (playbook == null || playbook.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing '" + AnsibleConstants.PLAYBOOK_KEY + "' parameter");
        }
        log.info("Using a playbook: {}", playbook);

        Path cfgFile = workDir.relativize(getCfgFile(args, workDir, tmpDir, debug));

        Path inventoryPath = workDir.relativize(getInventoryPath(args, workDir, tmpDir));

        Map<String, String> extraVars = (Map<String, String>) args.get(AnsibleConstants.EXTRA_VARS_KEY);

        Path attachmentsPath = workDir.relativize(workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME));

        Path vaultPasswordPath = getVaultPasswordFilePath(args, workDir, tmpDir);
        if (vaultPasswordPath != null) {
            vaultPasswordPath = workDir.relativize(vaultPasswordPath);
        }

        Path privateKeyPath = getPrivateKeyPath(args, workDir);
        if (privateKeyPath != null) {
            privateKeyPath = workDir.relativize(privateKeyPath);
        }

        final Map<String, String> env = addExtraEnv(defaultEnv(), args);

        processCallback(workDir);

        getConcordCfg(context, "apiKey")
                .ifPresent(key -> {
                    processLookup(workDir);
                    env.put("CONCORD_APIKEY", key);
                });

        PlaybookProcessBuilder b = pb.build(playbook, inventoryPath.toString())
                .withAttachmentsDir(toString(attachmentsPath))
                .withCfgFile(toString(cfgFile))
                .withPrivateKey(toString(privateKeyPath))
                .withVaultPasswordFile(toString(vaultPasswordPath))
                .withUser(trim(getString(args, AnsibleConstants.USER_KEY)))
                .withTags(trim(getString(args, AnsibleConstants.TAGS_KEY)))
                .withExtraVars(extraVars)
                .withLimit(getRetryFile(args))
                .withDebug(debug)
                .withVerboseLevel(getVerboseLevel(args))
                .withEnv(env);

        Process p = b.build();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            log.info("ANSIBLE: {}", line);
        }

        int code = p.waitFor();
        log.debug("execution -> done, code {}", code);

        updateAnsibleStats(workDir, code);

        if (code != SUCCESS_EXIT_CODE) {
            saveRetryFile(args, workDir);
            log.warn("Playbook is finished with code {}", code);
            throw new IllegalStateException("Process finished with with exit code " + code);
        }
    }

    private String getRetryFile(Map<String, Object> args) {
        boolean retry = getBoolean(args, AnsibleConstants.RETRY_KEY, false);
        if (retry) {
            return getNameWithoutExtension(getString(args, AnsibleConstants.PLAYBOOK_KEY)) + ".retry";
        }

        String limit = getString(args, AnsibleConstants.LIMIT_KEY);
        if (limit != null) {
            return limit;
        }

        return null;
    }

    private Map<String,String> defaultEnv() {
        final Map<String, String> env = new HashMap<>();
        env.put("PYTHONPATH", PYTHON_LIB_DIR);
        env.put("CONCORD_HOST", rpcCfg.getServerHost());
        env.put("CONCORD_PORT", String.valueOf(rpcCfg.getServerPort()));
        env.put("CONCORD_INSTANCE_ID", (String) context.getVariable(Constants.Context.TX_ID_KEY));
        env.put("CONCORD_BASEURL", apiCfg.getBaseUrl());
        return env;
    }

    private void processCallback(Path workDir) throws IOException {
        Path libDir = workDir.resolve(PYTHON_LIB_DIR);
        Files.createDirectories(libDir);

        copyResourceToFile("/server_pb2.py", libDir.resolve("server_pb2.py"));
        copyResourceToFile("/server_pb2_grpc.py", libDir.resolve("server_pb2_grpc.py"));

        Path callbackDir = workDir.resolve(CALLBACK_DIR);
        Files.createDirectories(callbackDir);
        copyResourceToFile("/com/walmartlabs/concord/plugins/ansible/callback/concord_events.py", callbackDir.resolve("concord_events.py"));
    }

    private void processLookup(Path workDir) {
        Path callbackDir = workDir.resolve(LOOKUP_DIR);
        try {
            Files.createDirectories(callbackDir);
            copyResourceToFile("/com/walmartlabs/concord/plugins/ansible/lookup/concord_inventory.py", callbackDir.resolve("concord_inventory.py"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copyResourceToFile(String resourceName, Path dest) throws IOException {
        try (InputStream is = RunPlaybookTask2.class.getResourceAsStream(resourceName)) {
            Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @SuppressWarnings("unchecked")
    public void run(String dockerImageName, Map<String, Object> args, String payloadPath) throws Exception {
        log.info("Using the docker image: {}", dockerImageName);
        run(args, payloadPath, (playbookPath, inventoryPath) ->
                new DockerPlaybookProcessBuilder(txId, dockerImageName, payloadPath, playbookPath, inventoryPath)
                        .withForcePull((boolean) args.getOrDefault(AnsibleConstants.FORCE_PULL_KEY, true)));
    }

    @SuppressWarnings("unchecked")
    public void run(Map<String, Object> args, String payloadPath) throws Exception {
        run(args, payloadPath, (playbookPath, inventoryPath) ->
                new PlaybookProcessBuilderImpl(payloadPath, playbookPath, inventoryPath));
    }

    @Override
    public void execute(Context ctx) throws Exception {
        Map<String, Object> args = new HashMap<>();

        addIfPresent(ctx, args, AnsibleConstants.CONFIG_KEY);
        addIfPresent(ctx, args, AnsibleConstants.PLAYBOOK_KEY);
        addIfPresent(ctx, args, AnsibleConstants.EXTRA_VARS_KEY);
        addIfPresent(ctx, args, AnsibleConstants.INVENTORY_KEY);
        addIfPresent(ctx, args, AnsibleConstants.INVENTORY_FILE_NAME);
        addIfPresent(ctx, args, AnsibleConstants.DYNAMIC_INVENTORY_FILE_NAME);
        addIfPresent(ctx, args, AnsibleConstants.USER_KEY);
        addIfPresent(ctx, args, AnsibleConstants.TAGS_KEY);
        addIfPresent(ctx, args, AnsibleConstants.DEBUG_KEY);
        addIfPresent(ctx, args, AnsibleConstants.VAULT_PASSWORD_KEY);
        addIfPresent(ctx, args, AnsibleConstants.VAULT_PASSWORD_FILE_KEY);
        addIfPresent(ctx, args, AnsibleConstants.VERBOSE_LEVEL_KEY);
        addIfPresent(ctx, args, AnsibleConstants.FORCE_PULL_KEY);
        addIfPresent(ctx, args, AnsibleConstants.PRIVATE_KEY_FILE_KEY);
        addIfPresent(ctx, args, AnsibleConstants.RETRY_KEY);
        addIfPresent(ctx, args, AnsibleConstants.LIMIT_KEY);
        addIfPresent(ctx, args, AnsibleConstants.SAVE_RETRY_FILE);

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
        throw new IOException("Inventory file not found: " + p.toAbsolutePath());
    }

    private static Map<String, Object> makeCfg(Map<String, Object> cfg, String baseDir) {
        Map<String, Object> m = new HashMap<>();
        m.put("defaults", makeDefaults(baseDir));
        m.put("ssh_connection", makeSshConnCfg());

        if (cfg != null) {
            return ConfigurationUtils.deepMerge(m, cfg);
        }

        return m;
    }

    private static Map<String, Object> makeDefaults(String baseDir) {
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
        if (!"".equals(baseDir) && !baseDir.endsWith("/")) {
            baseDir = baseDir + "/";
        }
        m.put("callback_plugins", baseDir + CALLBACK_DIR);
        m.put("lookup_plugins", baseDir + LOOKUP_DIR);

        return m;
    }

    private static Map<String, Object> makeSshConnCfg() {
        Map<String, Object> m = new HashMap<>();

        // use a shorter control_path to prevent path length errors
        m.put("control_path", "%(directory)s/%%h-%%p-%%r");

        // Default pipelining to True for better overall performance, compatibility
        m.put("pipelining", "True");

        return m;
    }

    private static Path getCfgFile(Map<String, Object> args, Path workDir, Path tmpDir, boolean debug) throws IOException {
        String s = (String) args.get(AnsibleConstants.CONFIG_FILE_KEY);
        if (s != null) {
            Path provided = workDir.resolve(s);
            if (Files.exists(provided)) {
                log.info("Using the provided configuration file: {}", provided);
                return provided;
            }
        }

        Map<String, Object> cfg = (Map<String, Object>) args.get(AnsibleConstants.CONFIG_KEY);
        return createCfgFile(tmpDir, makeCfg(cfg, ""), debug);
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
            if (password == null) {
                throw new IllegalArgumentException("Password is required to export a private key");
            }

            Map<String, String> keyPair = secretStore.exportKeyAsFile(txId, workDir.toAbsolutePath().toString(), name, password);
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

    private void saveRetryFile(Map<String, Object> args, Path workDir) throws IOException {
        boolean saveRetryFiles = getBoolean(args, AnsibleConstants.SAVE_RETRY_FILE, false);
        if (!saveRetryFiles) {
            return;
        }

        String playbookName = getString(args, AnsibleConstants.PLAYBOOK_KEY);
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

    private static String trim(String s) {
        return s != null ? s.trim() : null;
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
        Object v = args.get(AnsibleConstants.VERBOSE_LEVEL_KEY);
        if (v == null) {
            return 0;
        }

        if (v instanceof Integer) {
            return (Integer) v;
        }

        if (v instanceof Long) {
            return ((Long) v).intValue();
        }

        throw new IllegalArgumentException("'" + AnsibleConstants.VERBOSE_LEVEL_KEY + "' should be an integer: " + v);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> addExtraEnv(Map<String, String> env, Map<String, Object> m) {
        Map<String, String> extraEnv = (Map<String, String>) m.get(AnsibleConstants.EXTRA_ENV);
        if (extraEnv == null || extraEnv.isEmpty()) {
            return env;
        }

        Map<String, String> result = new HashMap<>(env);
        result.putAll(extraEnv);

        return result;
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> getConcordCfg(Context ctx, String key) {
        Map<String, String> cfg = (Map<String, String>) ctx.getVariable("concord");
        if (cfg == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(cfg.get(key));
    }

    private static boolean getBoolean(Map<String, Object> args, String key, boolean defaultValue) {
        Object v = args.get(key);
        if (v == null) {
            return defaultValue;
        }

        if (v instanceof Boolean) {
            return (Boolean) v;
        } else if (v instanceof String) {
            return Boolean.parseBoolean((String) v);
        } else {
            throw new IllegalArgumentException("Invalid boolean value '" + v + "' for key '" + key + "'");
        }
    }

    private static String getString(Map<String, Object> args, String key) {
        return (String)args.get(key);
    }

    private static String getNameWithoutExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        return (i == -1) ? fileName : fileName.substring(0, i);
    }
}