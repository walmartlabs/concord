package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.project.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * Executes a job in a separate JVM.
 */
@Named
public class JarJobExecutor implements JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(JarJobExecutor.class);

    private final LogManager logManager;
    private final Configuration cfg;

    @Inject
    public JarJobExecutor(LogManager logManager, Configuration cfg) {
        this.logManager = logManager;
        this.cfg = cfg;
    }

    @Override
    public void exec(String id, Path workDir, String entryPoint, Collection<String> jvmArgs) throws ExecutionException {
        String javaCmd = cfg.getAgentJavaCmd();

        String mainClass = Utils.getMainClass(workDir, entryPoint);

        // TODO pass an original ID?
        Collection<String> cmd = new ArrayList<>();
        cmd.add(javaCmd);
        cmd.addAll(jvmArgs);
        cmd.add("-DinstanceId=" + id);
        cmd.add("-cp");
        cmd.add(Constants.Files.LIBRARIES_DIR_NAME + "/*:" + entryPoint);
        cmd.add(mainClass);

        // start the process

        Process proc = start(id, workDir, entryPoint, cmd.toArray(new String[cmd.size()]));
        log.info("exec ['{}', '{}', '{}'] -> running...", id, workDir, entryPoint);

        // redirect the logs

        Runnable redirect = () -> logManager.store(id, proc.getInputStream());
        Thread redirectThread = new Thread(redirect, "Redirect: " + id);
        redirectThread.start();

        // TODO wait for the redirection thread?

        // wait for completion

        int code;
        try {
            code = proc.waitFor();
            logManager.log(id, "Process finished with: %s", code);
        } catch (Exception e) {
            throw handleError(id, workDir, entryPoint, proc, e);
        }

        if (code != 0) {
            log.warn("exec ['{}'] -> finished with {}", id, code);
            throw new ExecutionException("Process returned an error (" + code + "): " + String.join(" ", cmd));
        }

        log.info("exec ['{}'] -> finished with {}", id, code);
    }

    private Process start(String id, Path workDir, String entryPoint, String[] cmd) throws ExecutionException {
        String fullCmd = String.join(" ", cmd);

        try {
            log.info("exec ['{}', '{}', '{}'] -> executing: {}", id, workDir, entryPoint, fullCmd);
            logManager.log(id, "Starting: %s", fullCmd);

            ProcessBuilder b = new ProcessBuilder()
                    .directory(workDir.toFile())
                    .command(cmd)
                    .redirectErrorStream(true);

            Map<String, String> env = b.environment();
            env.put("_CONCORD_ATTACHMENTS_DIR", workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                    .toAbsolutePath().toString());

            return b.start();
        } catch (IOException e) {
            log.error("exec ['{}', '{}', '{}'] -> error while starting for a process", id, workDir, entryPoint);
            logManager.log(id, "Error: %s", e);
            throw new ExecutionException("Error starting a process: " + fullCmd, e);
        }
    }

    private ExecutionException handleError(String id, Path workDir, String entryPoint, Process proc, Throwable e) throws ExecutionException {
        log.warn("handleError ['{}', '{}', '{}'] -> execution error", id, workDir, entryPoint, e);
        logManager.log(id, "Interrupted");

        if (kill(proc)) {
            log.warn("exec ['{}', '{}', '{}'] -> killed", id, workDir, entryPoint);
        }

        throw new ExecutionException("Execution error: " + id, e);
    }

    private static boolean kill(Process proc) {
        if (!proc.isAlive()) {
            return false;
        }

        // TODO cfg?
        proc.destroy();
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // ignore
        }

        // TODO timeout?
        while (proc.isAlive()) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                // ignore
            }
            proc.destroyForcibly();
        }

        return true;
    }
}
