package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.rpc.AgentApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ServerConnector {

    private static final Logger log = LoggerFactory.getLogger(ServerConnector.class);

    private final ExecutorService executor;

    private AgentApiClient client;
    private Future<?> executionCleanup;
    private Future<?> commandHandler;
    private List<Future<?>> workers;
    private Future<?> dockerSweeper;

    public ServerConnector(ExecutorService executor) {
        this.executor = executor;
    }

    public void start(Configuration cfg) throws IOException {
        String agentId = cfg.getAgentId();
        String host = cfg.getServerHost();
        int port = cfg.getServerRpcPort();
        int workersCount = cfg.getWorkersCount();

        client = new AgentApiClient(agentId, host, port);
        log.info("start -> connecting to {}:{}", host, port);

        ExecutionManager executionManager = new ExecutionManager(client, cfg);

        executionCleanup = executor.submit(new ExecutionStatusCleanup(executionManager));

        commandHandler = executor.submit(new CommandHandler(client, executionManager, executor));

        workers = new ArrayList<>();
        for (int i = 0; i < workersCount; i++) {
            Future<?> f = executor.submit(new Worker(client, executionManager, cfg.getLogMaxDelay()));
            workers.add(f);
        }

        if (cfg.isDockerSweeperEnabled()) {
            long t = cfg.getDockerSweeperPeriod();
            dockerSweeper = executor.submit(new DockerSweeper(executionManager, t));
        }
    }

    public void stop() {
        if (dockerSweeper != null) {
            dockerSweeper.cancel(true);
            dockerSweeper = null;
        }

        if (commandHandler != null) {
            commandHandler.cancel(true);
            commandHandler = null;
        }

        if (workers != null) {
            for (Future<?> f : workers) {
                f.cancel(true);
            }
            workers = null;
        }

        if (client != null) {
            client.stop();
            client = null;
        }

        if (executionCleanup != null) {
            executionCleanup.cancel(true);
            executionCleanup = null;
        }
    }
}
