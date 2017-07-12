package com.walmartlabs.concord.plugins.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.Task;
import com.walmartlabs.concord.project.Constants;
import io.takari.bpm.api.BpmnError;
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

    private static final String TMP_DIR = "ansible_tmp";

    @SuppressWarnings("unchecked")
    public void run(String dockerImageName, Map<String, Object> args, String payloadPath) throws Exception {
        Files.createDirectories(Paths.get(payloadPath, TMP_DIR));

        String playbook = (String) args.get(AnsibleConstants.PLAYBOOK_KEY);
        if (playbook == null || playbook.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing '" + AnsibleConstants.PLAYBOOK_KEY + "' parameter");
        }

        Map<String, Object> cfg = (Map<String, Object>) args.get(AnsibleConstants.CONFIG_KEY);
        String cfgFile = createCfgFile(payloadPath, makeCfg(cfg, ""));

        String inventoryPath = getInventoryPath(args, payloadPath);
        Map<String, String> extraVars = (Map<String, String>) args.get(AnsibleConstants.EXTRA_VARS_KEY);

        String playbookPath = Paths.get(payloadPath, playbook).toString();
        log.info("Using the playbook: {}", playbookPath);

        String attachmentsDir = payloadPath + "/" + Constants.Files.JOB_ATTACHMENTS_DIR_NAME;

        String vaultPasswordFile = getVaultPasswordFilePath(args, payloadPath);

        DockerPlaybookProcessBuilder b = new DockerPlaybookProcessBuilder(payloadPath, playbookPath, inventoryPath)
                .dockerImageName(dockerImageName)
                .withAttachmentsDir(attachmentsDir)
                .withCfgFile(cfgFile)
                .withPrivateKey(getPrivateKeyPath(payloadPath))
                .withUser(trim((String) args.get("user")))
                .withTags(trim((String) args.get("tags")))
                .withVaultPasswordFile(vaultPasswordFile)
                .withExtraVars(extraVars);

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
            throw new BpmnError("ansibleError", new IllegalStateException("Process finished with with exit code " + code));
        }
    }

    @SuppressWarnings("unchecked")
    public void run(Map<String, Object> args, String payloadPath) throws Exception {
        Files.createDirectories(Paths.get(payloadPath, TMP_DIR));

        Map<String, Object> cfg = (Map<String, Object>) args.get(AnsibleConstants.CONFIG_KEY);
        String cfgFile = createCfgFile(payloadPath, makeCfg(cfg, payloadPath));

        String playbook = (String) args.get(AnsibleConstants.PLAYBOOK_KEY);
        if (playbook == null || playbook.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing '" + AnsibleConstants.PLAYBOOK_KEY + "' parameter");
        }

        String inventoryPath = getInventoryPath(args, payloadPath);
        Map<String, String> extraVars = (Map<String, String>) args.get(AnsibleConstants.EXTRA_VARS_KEY);

        String playbookPath = Paths.get(payloadPath, playbook).toAbsolutePath().toString();
        log.info("Using the playbook: {}", playbookPath);

        String attachmentsDir = payloadPath + "/" + Constants.Files.JOB_ATTACHMENTS_DIR_NAME;

        String vaultPasswordFile = getVaultPasswordFilePath(args, payloadPath);

        PlaybookProcessBuilder b = new PlaybookProcessBuilder(playbookPath, inventoryPath)
                .withAttachmentsDir(attachmentsDir)
                .withCfgFile(cfgFile)
                .withPrivateKey(getPrivateKeyPath(payloadPath))
                .withUser(trim((String) args.get("user")))
                .withTags(trim((String) args.get("tags")))
                .withVaultPasswordFile(vaultPasswordFile)
                .withExtraVars(extraVars);

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
            throw new BpmnError("ansibleError", new IllegalStateException("Process finished with with exit code " + code));
        }
    }

    @SuppressWarnings("unchecked")
    private static String getInventoryPath(Map<String, Object> args, String payloadPath) throws IOException {
        // try an "inline" inventory
        Object v = args.get(AnsibleConstants.INVENTORY_KEY);
        if (v instanceof Map) {
            Path p = createInventoryFile(payloadPath, (Map<String, Object>) v);
            updateDynamicInventoryPermissions(p);
            return p.toAbsolutePath().toString();
        }

        // TODO check if "inventory" is actually a path to a file

        // try a static inventory file
        Path p = Paths.get(payloadPath, AnsibleConstants.INVENTORY_FILE_NAME);
        if (!Files.exists(p)) {
            // try a dynamic inventory script
            p = Paths.get(payloadPath, AnsibleConstants.DYNAMIC_INVENTORY_FILE_NAME);
            if (!Files.exists(p)) {
                // we can't continue without an inventory
                throw new IOException("Inventory file not found: " + p.toAbsolutePath());
            }

            updateDynamicInventoryPermissions(p);
            log.debug("getInventoryPath ['{}'] -> dynamic inventory", payloadPath);
        }
        return p.toAbsolutePath().toString();
    }

    private static Map<String, Object> makeCfg(Map<String, Object> cfg, String baseDir) {
        Map<String, Object> m = new HashMap<>();
        m.put("defaults", makeDefaults(baseDir));
        m.put("ssh_connection", makeSshConnCfg());

        if (cfg != null) {
            ConfigurationUtils.deepMerge(m, cfg);
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
        if(!"".equals(baseDir) && !baseDir.endsWith("/")) {
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

    @SuppressWarnings("unchecked")
    private static String createCfgFile(String payloadPath, Map<String, Object> cfg) throws IOException {
        StringBuilder b = new StringBuilder();

        for (Map.Entry<String, Object> c : cfg.entrySet()) {
            String k = c.getKey();
            Object v = c.getValue();
            if (!(v instanceof Map)) {
                throw new IllegalArgumentException("Invalid configuration. Expected a JSON object: " + k + ", got: " + v);
            }

            b = addCfgSection(b, k, (Map<String, Object>) v);
        }

        log.info("Using the configuration: \n{}", b);

        Path tmpFile = Files.createFile(Paths.get(payloadPath, TMP_DIR, "ansible.cfg"));
        Files.write(tmpFile, b.toString().getBytes(StandardCharsets.UTF_8));

        log.debug("createCfgFile -> done, created {}", tmpFile);
        return tmpFile.toAbsolutePath().toString();
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

    private static String getPrivateKeyPath(String payloadPath) throws IOException {
        Path p = Paths.get(payloadPath, AnsibleConstants.PRIVATE_KEY_FILE_NAME);
        if (!Files.exists(p)) {
            return null;
        }

        // ensure that the key has proper permissions (chmod 600)
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        Files.setPosixFilePermissions(p, perms);

        return p.toAbsolutePath().toString();
    }

    private static String getVaultPasswordFilePath(Map<String, Object> args, String payloadPath) throws IOException {
        // try an "inline" password first
        Object v = args.get(AnsibleConstants.VAULT_PASSWORD_KEY);
        if (v instanceof String) {
            Path p = Files.createFile(Paths.get(payloadPath, TMP_DIR, "vault_password"));
            Files.write(p, ((String) v).getBytes());
            return p.toAbsolutePath().toString();
        } else if (v != null) {
            throw new IllegalArgumentException("Invalid '" + AnsibleConstants.VAULT_PASSWORD_KEY + "' type: " + v);
        }

        Path p = Paths.get(payloadPath, AnsibleConstants.VAULT_PASSWORD_FILE_PATH);
        if (!Files.exists(p)) {
            return null;
        }
        return p.toAbsolutePath().toString();
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

    private static Path createInventoryFile(String payloadPath, Map<String, Object> m) throws IOException {
        Path p = Files.createFile(Paths.get(payloadPath, TMP_DIR, "inventory.sh"));

        try (BufferedWriter w = Files.newBufferedWriter(p)) {
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
        Files.setPosixFilePermissions(p, perms);
    }
}
