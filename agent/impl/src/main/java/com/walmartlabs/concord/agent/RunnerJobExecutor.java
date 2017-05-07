package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.project.Constants;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

@Named
public class RunnerJobExecutor extends JarJobExecutor {

    @Inject
    public RunnerJobExecutor(Configuration cfg, LogManager logManager, DependencyManager dependencyManager, ExecutorService executorService) {
        super(cfg, logManager, dependencyManager, executorService);
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
}
