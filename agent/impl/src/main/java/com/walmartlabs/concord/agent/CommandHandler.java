package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.rpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);
    private static final long ERROR_DELAY = 5000;

    private final AgentApiClient client;
    private final ExecutionManager executionManager;

    public CommandHandler(AgentApiClient client, ExecutionManager executionManager) {
        this.client = client;
        this.executionManager = executionManager;
    }

    @Override
    public void run() {
        CommandQueue ch = client.getCommandQueue();

        while (!Thread.currentThread().isInterrupted()) {
            Command cmd;
            try {
                cmd = ch.take();
            } catch (ClientException e) {
                log.warn("run -> transport error: {}", e.getMessage());
                sleep(ERROR_DELAY);
                continue;
            }

            execute(cmd);
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
