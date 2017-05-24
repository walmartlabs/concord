package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.rpc.AgentApiClient;
import com.walmartlabs.concord.rpc.ClientException;
import com.walmartlabs.concord.rpc.JobQueue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipOutputStream;

public class RunnerJobExecutor extends JarJobExecutor {

    private final AgentApiClient client;

    public RunnerJobExecutor(Configuration cfg, LogManager logManager, DependencyManager dependencyManager,
                             ExecutorService executorService, AgentApiClient client) {

        super(cfg, logManager, dependencyManager, executorService);
        this.client = client;
    }

    @Override
    protected String getMainClass(Path workDir, String entryPoint) throws ExecutionException {
        return "com.walmartlabs.concord.runner.Main";
    }

    @Override
    protected String createClassPath(String entryPoint) {
        String runnerPath = getCfg().getRunnerPath().normalize().toString();
        return Constants.Files.LIBRARIES_DIR_NAME + "/*:" + runnerPath;
    }

    @Override
    protected void postProcess(String instanceId, Path workDir) throws ExecutionException {
        super.postProcess(instanceId, workDir);

        Path attachmentsDir = workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME);
        if (!Files.exists(attachmentsDir)) {
            return;
        }

        JobQueue q = client.getJobQueue();

        // send attachments

        Path tmp;
        try {
            // TODO cfg
            tmp = Files.createTempFile("attachments", ".zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp))) {
                IOUtils.zip(zip, attachmentsDir);
            }
        } catch (IOException e) {
            throw new ExecutionException("Error while archiving the attachments: " + instanceId, e);
        }

        try {
            q.uploadAttachments(instanceId, tmp);
        } catch (ClientException e) {
            throw new ExecutionException("Error while sending the attachments: " + instanceId, e);
        }
    }
}
