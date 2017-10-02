package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.common.DockerProcessBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DockerSweeper implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DockerSweeper.class);

    private static final String[] PS_CMD = {"docker", "ps", "-a",
            "--filter", "label=" + DockerProcessBuilder.CONCORD_TX_ID_LABEL,
            "--format", "{{.Label \"" + DockerProcessBuilder.CONCORD_TX_ID_LABEL + "\"}} {{.ID}}"};

    private static final long PERIOD_DELAY = TimeUnit.MINUTES.toMillis(15);
    private static final long RETRY_DELAY = TimeUnit.SECONDS.toMillis(30);

    private final ExecutionManager executionManager;

    public DockerSweeper(ExecutionManager executionManager) {
        this.executionManager = executionManager;
    }

    @Override
    public void run() {
        log.info("run -> removing orphaned Docker containers...");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Map<String, String> containers = findContainers();
                log.info("run -> found {} container(s)...", containers.size());

                for (Map.Entry<String, String> c : containers.entrySet()) {
                    String txId = c.getKey();
                    if (executionManager.isRunning(txId)) {
                        continue;
                    }

                    String cId = c.getValue();
                    log.warn("run -> found an orphaned container {} (process {}), attempting to kill...", cId, txId);
                    killContainer(cId);
                }

                sleep(PERIOD_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("run -> error: {}, retrying in {}ms...", e.getMessage(), RETRY_DELAY);
                sleep(RETRY_DELAY);
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, String> findContainers() throws IOException, InterruptedException {
        Process b = new ProcessBuilder()
                .command(PS_CMD)
                .redirectErrorStream(true)
                .start();

        int code = b.waitFor();
        if (code != 0) {
            throw new IOException("Error while retrieving the list of containers: docker exit code " + code);
        }

        Map<String, String> ids = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(b.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                int idx = line.indexOf(" ");
                if (idx < 0 || idx + 1 >= line.length()) {
                    log.warn("findContainers -> invalid line: {}", line);
                    continue;
                }

                String k = line.substring(0, idx);
                String v = line.substring(idx + 1, line.length());

                ids.put(k, v);
            }
        }

        return ids;
    }

    private static void killContainer(String cId) throws IOException, InterruptedException {
        Process b = new ProcessBuilder()
                .command(createKillCommand(cId))
                .start();

        int code = b.waitFor();
        if (code != 0) {
            throw new IOException("Error while removing a container " + cId + ": docker exit code " + code);
        }

        log.info("killContainer -> done, {} removed", cId);
    }

    private static String[] createKillCommand(String cId) {
        return new String[]{"docker", "rm", "-f", cId};
    }
}
