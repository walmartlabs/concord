package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.DockerProcessBuilder;
import com.walmartlabs.concord.common.Task;
import io.takari.bpm.api.BpmnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

@Named("docker")
public class DockerTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(DockerTask.class);

    private static final int SUCCESS_EXIT_CODE = 0;
    private static final String VOLUME_CONTAINER_DEST = "/workspace";

    public void call(String dockerImage, String cmd, String payloadPath) throws Exception {
        try {
            Process p = new DockerProcessBuilder(dockerImage)
                    .cleanup(true)
                    .volume(payloadPath, VOLUME_CONTAINER_DEST)
                    .args(toList(cmd))
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

    private static List<String> toList(String args) {
        StringTokenizer st = new StringTokenizer(args);
        List<String> result = new ArrayList<>();
        for (int i = 0; st.hasMoreTokens(); i++) {
            result.add(st.nextToken());
        }
        return result;
    }
}
