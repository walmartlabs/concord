package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.rpc.AgentApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;

public class ServerConnector {

    private static final Logger log = LoggerFactory.getLogger(ServerConnector.class);

    private AgentApiClient client;
    private Thread[] workers;
    private Thread executionCleanup;
    private Thread commandHandler;
    private Thread dockerSweeper;

    public void start(Configuration cfg) throws IOException {
        String agentId = cfg.getAgentId();
        String host = cfg.getServerHost();
        int port = cfg.getServerRpcPort();
        int workersCount = cfg.getWorkersCount();

        client = new AgentApiClient(agentId, host, port);
        log.info("start -> connecting to {}:{}", host, port);

        ExecutionManager executionManager = new ExecutionManager(client, cfg);

        executionCleanup = new Thread(new ExecutionStatusCleanup(executionManager),
                "execution-status-cleanup");
        executionCleanup.start();

        commandHandler = new Thread(new CommandHandler(client, executionManager, Executors.newCachedThreadPool()),
                "command-handler");
        commandHandler.start();

        workers = new Thread[workersCount];
        for (int i = 0; i < workersCount; i++) {
            Worker w = new Worker(client, executionManager, cfg.getLogMaxDelay());
            Thread t = new Thread(w, "worker-" + i);
            workers[i] = t;
        }

        for (Thread w : workers) {
            w.start();
        }

        if (cfg.isDockerSweeperEnabled()) {
            long t = cfg.getDockerSweeperPeriod();
            dockerSweeper = new Thread(new DockerSweeper(executionManager, t),
                    "docker-sweeper");
            dockerSweeper.start();
        }
    }

    public synchronized void stop() {
        if (dockerSweeper != null) {
            dockerSweeper.interrupt();
            dockerSweeper = null;
        }

        if (commandHandler != null) {
            commandHandler.interrupt();
            commandHandler = null;
        }

        if (workers != null) {
            for (Thread w : workers) {
                w.interrupt();
            }
            workers = null;
        }

        if (client != null) {
            client.stop();
            client = null;
        }

        if (executionCleanup != null) {
            executionCleanup.interrupt();
            executionCleanup = null;
        }
    }
}
