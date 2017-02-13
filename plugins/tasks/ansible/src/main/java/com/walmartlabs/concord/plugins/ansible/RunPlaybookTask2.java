package com.walmartlabs.concord.plugins.ansible;

import com.walmartlabs.concord.common.Task;
import io.takari.bpm.api.BpmnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Named
public class RunPlaybookTask2 implements Task {

    private static final Logger log = LoggerFactory.getLogger(RunPlaybookTask2.class);

    private static final int SUCCESS_EXIT_CODE = 0;

    @Override
    public String getKey() {
        return "ansible2";
    }

    public void run(Map<String, Object> args, String payloadPath) throws Exception {
        String cfgFile = createCfgFile();

        // TODO constants
        String playbook = (String) args.get("playbook");
        if (playbook == null || playbook.trim().isEmpty()) {
            throw new IllegalArgumentException("The 'playbook' parameter is missing");
        }

        String inventory = getInventoryPath(payloadPath);
        Map<String, String> extraVars = (Map<String, String>) args.get("extraVars");

        String playbookPath = Paths.get(payloadPath, playbook).toAbsolutePath().toString();
        log.info("Using the playbook: {}", playbookPath);

        PlaybookProcessBuilder b = new PlaybookProcessBuilder(playbookPath, inventory)
                .withCfgFile(cfgFile)
                .withPrivateKey(getPrivateKeyPath(payloadPath))
                .withUser(trim((String) args.get("user")))
                .withTags(trim((String) args.get("tags")))
                .withExtraVars(extraVars);

        Process p = b.build();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            log.info("ANSIBLE: {}", line);
        }

        int code = p.waitFor();
        log.debug("execution -> done, code {}", code);

        if (code != SUCCESS_EXIT_CODE) {
            log.warn("Playbook is finished with code {}", code);
            throw new BpmnError("ansibleError", new IllegalStateException("Process finished with with exit code " + code));
        }
    }

    private static String getInventoryPath(String payloadPath) throws IOException {
        // try a static inventory file first
        Path p = Paths.get(payloadPath, AnsibleConstants.INVENTORY_FILE_NAME);
        if (!Files.exists(p)) {
            // try a dynamic inventory script
            p = Paths.get(payloadPath, AnsibleConstants.DYNAMIC_INVENTORY_FILE_NAME);
            if (!Files.exists(p)) {
                // we can't continue without an inventory
                throw new IOException("Inventory file not found: " + p.toAbsolutePath());
            }

            // ensure that a dynamic inventory script has the executable bit set
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(p, perms);
            log.debug("getInventoryPath ['{}'] -> dynamic inventory", payloadPath);
        }
        return p.toAbsolutePath().toString();
    }

    private static String createCfgFile() throws IOException {
        Path tmpFile = Files.createTempFile("ansible", ".cfg");

        String template = "[defaults]\n" +
                "host_key_checking = False\n" +
                "remote_tmp = /tmp/ansible/$USER\n" +
                "[ssh_connection]\n" +
                "control_path = %(directory)s/%%h-%%p-%%r\n" +
                "pipelining=true";

        try (OutputStream out = Files.newOutputStream(tmpFile)) {
            out.write(template.getBytes());
        }

        log.debug("createCfgFile -> done, created {}", tmpFile);
        return tmpFile.toAbsolutePath().toString();
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

    private static String trim(String s) {
        return s != null ? s.trim() : null;
    }
}
