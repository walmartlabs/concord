package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.rpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public class CommandHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);
    private static final long RETRY_DELAY = 5000;

    private final AgentApiClient client;
    private final ExecutionManager executionManager;
    private final ExecutorService executor;

    public CommandHandler(AgentApiClient client, ExecutionManager executionManager, ExecutorService executor) {
        this.client = client;
        this.executionManager = executionManager;
        this.executor = executor;
    }

    @Override
    public void run() {
        CommandQueue ch = client.getCommandQueue();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                ch.stream(new com.walmartlabs.concord.rpc.CommandHandler() {
                    @Override
                    public void onCommand(Command cmd) {
                        executor.submit(() -> execute(cmd));
                    }

                    @Override
                    public void onError(Throwable t) {
                        log.warn("run -> error while streaming commands from the server: {}", t.getMessage());
                    }
                });
            } catch (ClientException e) {
                log.warn("run -> transport error: {}", e.getMessage());
            }

            sleep(RETRY_DELAY);
        }
    }

    private void execute(Command cmd) {
        log.info("execute -> got a command: {}", cmd);

        if (cmd instanceof CancelJobCommand) {
            CancelJobCommand c = (CancelJobCommand) cmd;
            executionManager.cancel(c.getInstanceId());
        } else {
            log.warn("execute -> unsupported command type: " + cmd.getClass());
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
