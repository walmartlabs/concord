package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.server.api.agent.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ServerConnector {

    private static final Logger log = LoggerFactory.getLogger(ServerConnector.class);

    private final ExecutorService executor;

    private Client client;
    private Future<?> commandHandler;
    private List<Future<?>> workers;

    public ServerConnector(ExecutorService executor) {
        this.executor = executor;
    }

    public void start(Configuration cfg) {
        String agentId = UUID.randomUUID().toString();
        String host = cfg.getServerHost();
        int port = cfg.getServerPort();

        // TODO cfg
        int workersCount = 3;

        client = new Client(agentId, host, port);
        log.info("start -> connected to {}:{}", host, port);

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
