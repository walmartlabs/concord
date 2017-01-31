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
import java.nio.file.StandardCopyOption;
import java.util.Map;

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
        // TODO refactor
        String playbook = (String) args.get("playbook");
        if (playbook == null || playbook.trim().isEmpty()) {
            throw new IllegalArgumentException("The 'playbook' parameter is missing");
        }

        String inventory = createInventory(payloadPath);
        Map<String, String> extraVars = (Map<String, String>) args.get("extraVars");

        String playbookPath = Paths.get(payloadPath, playbook).toAbsolutePath().toString();
        log.info("Using the playbook: {}", playbookPath);

        PlaybookProcessBuilder b = new PlaybookProcessBuilder(playbookPath, inventory)
                .withCfgFile(cfgFile)
                .withExtraVars(extraVars);

        String user = (String) args.get("user");
        if (user != null && !user.trim().isEmpty()) {
            b.withUser(user);
        }

        String tags = (String) args.get("tags");
        if (tags != null && !tags.trim().isEmpty()) {
            b.withTags(tags);
        }

        Process p = b.build();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            log.info("ANSIBLE: {}", line);
        }

        int code = p.waitFor();
        if (code == SUCCESS_EXIT_CODE) {
            log.debug("execution -> done");
        } else {
            log.warn("Playbook is finished with code {}", code);
            log.debug("execution -> finished with exit code {}", code);
            throw new BpmnError("ansibleError", new IllegalStateException("Process finished with with exit code " + code));
        }
    }

    private static String createInventory(String payloadPath) throws IOException {
        Path p = Paths.get(payloadPath, AnsibleConstants.GENERATED_INVENTORY_FILE_NAME);
        if (!Files.exists(p)) {
            throw new IOException("Inventory file not found: " + p.toAbsolutePath());
        }

        Path tmpFile = Files.createTempFile("inventory", ".ini");
        Files.copy(p, tmpFile, StandardCopyOption.REPLACE_EXISTING);
        return tmpFile.toAbsolutePath().toString();
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

        log.debug("createCfgFile ['{}'] -> done, created {}", tmpFile);
        return tmpFile.toAbsolutePath().toString();
    }
}
