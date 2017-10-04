package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.DockerProcessBuilder;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.Task;
import io.takari.bpm.api.BpmnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

@Named("docker")
public class DockerTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(DockerTask.class);

    private static final int SUCCESS_EXIT_CODE = 0;
    private static final String VOLUME_CONTAINER_DEST = "/workspace";

    @InjectVariable(Constants.Context.TX_ID_KEY)
    String txId;

    public void call(String dockerImage, boolean forcePull, String cmd, Map<String, Object> env, String payloadPath) throws Exception {
        try {
            createRunScript(payloadPath, cmd);

            Process p = new DockerProcessBuilder(dockerImage)
                    .addLabel(DockerProcessBuilder.CONCORD_TX_ID_LABEL, txId)
                    .cleanup(true)
                    .volume(payloadPath, VOLUME_CONTAINER_DEST)
                    .arg("/workspace/.docker_cmd.sh")
                    .env(stringify(env))
                    .forcePull(forcePull)
                    .build();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("DOCKER: {}", line);
            }

            int code = p.waitFor();

            if (code != SUCCESS_EXIT_CODE) {
                log.warn("call ['{}', '{}', '{}'] -> finished with code {}", dockerImage, cmd, payloadPath, code);
                throw new BpmnError("dockerError",
                        new IllegalStateException("Docker process finished with with exit code " + code));
            }

            log.info("call ['{}', '{}', '{}'] -> done", dockerImage, cmd, payloadPath);
        } catch (BpmnError e) {
            throw e;
        } catch (Exception e) {
            log.error("call ['{}', '{}', '{}'] -> error", dockerImage, cmd, payloadPath, e);
            throw new BpmnError("dockerError", e);
        }
    }

    private static void createRunScript(String path, String cmd) throws IOException {
        Path p = Paths.get(path, ".docker_cmd.sh");

        String script = "#!/bin/sh\n" +
                "cd " + VOLUME_CONTAINER_DEST + "\n" +
                cmd;

        Files.write(p, script.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        updateScriptPermissions(p);
    }

    private static void updateScriptPermissions(Path p) throws IOException {
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(p, perms);
    }

    private static Map<String, String> stringify(Map<String, Object> m) {
        if (m == null || m.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }

            result.put(e.getKey(), v.toString());
        }

        return result;
    }
}
