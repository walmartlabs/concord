package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.rpc.AgentApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ServerConnector {

    private static final Logger log = LoggerFactory.getLogger(ServerConnector.class);

    private final ExecutorService executor;

    private AgentApiClient client;
    private Future<?> commandHandler;
    private List<Future<?>> workers;

    public ServerConnector(ExecutorService executor) {
        this.executor = executor;
    }

    public void start(Configuration cfg) {
        String agentId = cfg.getAgentId();
        String host = cfg.getServerHost();
        int port = cfg.getServerPort();
        int workersCount = cfg.getWorkersCount();

        client = new AgentApiClient(agentId, host, port);
        log.info("start -> connecting to {}:{}", host, port);

        ExecutionManager executionManager = new ExecutionManager(client, cfg);
        commandHandler = executor.submit(new CommandHandler(client, executionManager));

        workers = new ArrayList<>();
        for (int i = 0; i < workersCount; i++) {
            Future<?> f = executor.submit(new Worker(client, executionManager));
            workers.add(f);
        }
    }

    public void stop() {
        if (commandHandler != null) {
            commandHandler.cancel(true);
        }

        if (workers != null) {
            for (Future<?> f : workers) {
                f.cancel(true);
            }
        }

        if (client != null) {
            client.stop();
        }
    }
}
