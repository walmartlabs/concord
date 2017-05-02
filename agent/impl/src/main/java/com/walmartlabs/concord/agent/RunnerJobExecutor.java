package com.walmartlabs.concord.agent;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

@Named
public class RunnerJobExecutor implements JobExecutor {

    private final Configuration cfg;
    private final JarJobExecutor jarExecutor;

    @Inject
    public RunnerJobExecutor(Configuration cfg, JarJobExecutor jarExecutor) {
        this.cfg = cfg;
        this.jarExecutor = jarExecutor;
    }

    @Override
    public void exec(String id, Path workDir, String entryPoint, Collection<String> jvmArgs) throws ExecutionException {
        String mainClass = "com.walmartlabs.concord.runner.Main";
        Collection<String> runnerCp = Collections.singleton(cfg.getRunnerPath().normalize().toString());
        jarExecutor.exec(id, workDir, mainClass, jvmArgs, runnerCp);
    }
}
