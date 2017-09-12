package com.walmartlabs.concord.plugins.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Named("ansible2")
public class RunPlaybookTask2 implements Task {

    private static final Logger log = LoggerFactory.getLogger(RunPlaybookTask2.class);

    private static final int SUCCESS_EXIT_CODE = 0;

    private void run(Map<String, Object> args, String payloadPath, PlaybookProcessBuilderFactory pb) throws Exception {
        boolean debug = toBoolean(args.get(AnsibleConstants.DEBUG_KEY));

        Path workDir = Paths.get(payloadPath);
        Path tmpDir = Files.createTempDirectory(workDir, "ansible");

        String playbook = (String) args.get(AnsibleConstants.PLAYBOOK_KEY);
        if (playbook == null || playbook.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing '" + AnsibleConstants.PLAYBOOK_KEY + "' parameter");
        }
        log.info("Using a playbook: {}", playbook);

        Path cfgFile = workDir.relativize(getCfgFile(args, workDir, tmpDir, debug));

        Path inventoryPath = workDir.relativize(getInventoryPath(args, workDir, tmpDir));

        Map<String, String> extraVars = (Map<String, String>) args.get(AnsibleConstants.EXTRA_VARS_KEY);

        Path attachmentsPath = workDir.relativize(workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME));

        Path vaultPasswordPath = getVaultPasswordFilePath(args, workDir, tmpDir);

        Path privateKeyPath = getPrivateKeyPath(workDir);
        if (privateKeyPath != null) {
            privateKeyPath = workDir.relativize(privateKeyPath);
        }

        PlaybookProcessBuilder b = pb.build(playbook, inventoryPath.toString())
                .withAttachmentsDir(toString(attachmentsPath))
                .withCfgFile(toString(cfgFile))
                .withPrivateKey(toString(privateKeyPath))
                .withVaultPasswordFile(toString(vaultPasswordPath))
                .withUser(trim((String) args.get(AnsibleConstants.USER_KEY)))
                .withTags(trim((String) args.get(AnsibleConstants.TAGS_KEY)))
                .withExtraVars(extraVars)
                .withDebug(debug)
                .withVerboseLevel(getVerboseLevel(args));

        Process p = b.build();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            log.info("ANSIBLE: {}", line);
        }

        int code = p.waitFor();
        log.debug("execution -> done, code {}", code);

        updateAttachment(payloadPath, code);

        if (code != SUCCESS_EXIT_CODE) {
            log.warn("Playbook is finished with code {}", code);
            throw new IllegalStateException("Process finished with with exit code " + code);
        }
    }

    @SuppressWarnings("unchecked")
    public void run(String dockerImageName, Map<String, Object> args, String payloadPath) throws Exception {
        run(args, payloadPath, (playbookPath, inventoryPath) ->
                new DockerPlaybookProcessBuilder(dockerImageName, payloadPath, playbookPath, inventoryPath));
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

    private static boolean toBoolean(Object v) {
        if (v == null) {
            return false;
        }

        if (v instanceof Boolean) {
            return (Boolean) v;
        } else if (v instanceof String) {
            return Boolean.parseBoolean((String) v);
        } else {
            throw new IllegalArgumentException("Invalid boolean value: " + v);
        }
    }

    @SuppressWarnings("unchecked")
    private static Path getInventoryPath(Map<String, Object> args, Path workDir, Path tmpDir) throws IOException {
        // try an "inline" inventory
        Object v = args.get(AnsibleConstants.INVENTORY_KEY);
        if (v instanceof Map) {
            Path p = createInventoryFile(tmpDir, (Map<String, Object>) v);
            updateDynamicInventoryPermissions(p);
            log.info("Using an inline inventory");
            return p;
        }

        // try a static inventory file
        v = args.get(AnsibleConstants.INVENTORY_FILE_KEY);
        if (v != null) {
            Path p = workDir.resolve(v.toString());
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
            updateDynamicInventoryPermissions(p);
            log.info("Using a dynamic inventory script: {}", p);
            return p;
        }

        // try an "old school" dynamic inventory script
        p = workDir.resolve(AnsibleConstants.DYNAMIC_INVENTORY_FILE_NAME);
        if (Files.exists(p)) {
            updateDynamicInventoryPermissions(p);
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

        // disable ssl host key checking by default
        m.put("host_key_checking", false);

        // use a shorter path to store temporary files
        m.put("remote_tmp", "/tmp/ansible/$USER");

        // add plugins path
        if (!"".equals(baseDir) && !baseDir.endsWith("/")) {
            baseDir = baseDir + "/";
        }
        m.put("callback_plugins", baseDir + "_callbacks");

        return m;
    }

    private static Map<String, Object> makeSshConnCfg() {
        Map<String, Object> m = new HashMap<>();

        // use a shorter control_path to prevent path length errors
        m.put("control_path", "%(directory)s/%%h-%%p-%%r");

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

    private static Path getPrivateKeyPath(Path workDir) throws IOException {
        Path p = workDir.resolve(AnsibleConstants.PRIVATE_KEY_FILE_NAME);
        if (!Files.exists(p)) {
            return null;
        }

        // ensure that the key has proper permissions (chmod 600)
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        Files.setPosixFilePermissions(p, perms);

        return p;
    }

    private static Path getVaultPasswordFilePath(Map<String, Object> args, Path workDir, Path tmpDir) throws IOException {
        // try an "inline" password first
        Object v = args.get(AnsibleConstants.VAULT_PASSWORD_KEY);
        if (v instanceof String) {
            Path p = tmpDir.resolve("vault_password");
            Files.write(p, ((String) v).getBytes(), StandardOpenOption.CREATE);
            return p;
        } else if (v != null) {
            throw new IllegalArgumentException("Invalid '" + AnsibleConstants.VAULT_PASSWORD_KEY + "' type: " + v);
        }

        Path p = workDir.resolve(AnsibleConstants.VAULT_PASSWORD_FILE_PATH);
        if (!Files.exists(p)) {
            return null;
        }
        return p;
    }

    @SuppressWarnings("unchecked")
    private static void updateAttachment(String payloadPath, int code) throws IOException {
        Path p = Paths.get(payloadPath, Constants.Files.JOB_ATTACHMENTS_DIR_NAME, AnsibleConstants.STATS_FILE_NAME);

        ObjectMapper om = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        Map<String, Object> m = new HashMap<>();
        if (Files.exists(p)) {
            try (InputStream in = Files.newInputStream(p)) {
                Map<String, Object> mm = om.readValue(in, Map.class);
                m.putAll(mm);
            }
        }

        m.put(AnsibleConstants.EXIT_CODE_KEY, code);

        try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            om.writeValue(out, m);
        }
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

    private static void updateDynamicInventoryPermissions(Path p) throws IOException {
        // ensure that a dynamic inventory script has the executable bit set
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
}
