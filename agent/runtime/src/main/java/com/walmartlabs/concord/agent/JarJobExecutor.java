package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Executes a job in a separate JVM.
 */
@Named
public class JarJobExecutor implements JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(JarJobExecutor.class);

    private final LogManager logManager;

    @Inject
    public JarJobExecutor(LogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    public void exec(String id, Path workDir, String entryPoint) throws ExecutionException {
        // TODO cfg
        String javaCmd = "java";

        String mainClass = Utils.getMainClass(workDir, entryPoint);

        // TODO support for user-defined JVM opts
        // TODO pass through an original ID?
        String[] cmd = {javaCmd, "-Xmx512m",
                "-DinstanceId=" + id, "-Djavax.el.varArgs=true",
                "-cp", Constants.LIBRARIES_DIR_NAME + "/*:" + entryPoint,
                mainClass
        };


        // start the process

        Process proc = start(id, workDir, entryPoint, cmd);
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
            log.warn("exec ['{}', '{}', '{}'] -> finished with {}", id, workDir, entryPoint, code);
            throw new ExecutionException("Process returned an error (" + code + "): " + String.join(" ", cmd));
        }

        log.info("exec ['{}', '{}', '{}'] -> finished with {}", id, workDir, entryPoint, code);
    }

    private Process start(String id, Path workDir, String entryPoint, String[] cmd) throws ExecutionException {
        String fullCmd = String.join(" ", cmd);

        try {
            log.info("exec ['{}', '{}', '{}'] -> executing: {}", id, workDir, entryPoint, fullCmd);
            logManager.log(id, "Starting: %s", fullCmd);

            return new ProcessBuilder()
                    .directory(workDir.toFile())
                    .command(cmd)
                    .redirectErrorStream(true)
                    .start();
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
