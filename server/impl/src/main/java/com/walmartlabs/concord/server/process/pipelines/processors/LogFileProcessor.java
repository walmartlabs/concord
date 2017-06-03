package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.LogManager;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.keys.HeaderKey;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Creates a log file for a process.
 */
@Named
public class LogFileProcessor implements PayloadProcessor {

    /**
     * Path to a log file.
     */
    public static final HeaderKey<Path> LOG_FILE_PATH = HeaderKey.register("_logFilePath", Path.class);

    /**
     * Name of a log file, relative to the base directory of the log storage.
     */
    public static final HeaderKey<String> LOG_FILE_NAME = HeaderKey.register("_logFileName", String.class);

    private final LogManager logManager;

    @Inject
    public LogFileProcessor(LogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        try {
            String name = logManager.getFileName(payload.getInstanceId());
            Path path = logManager.getPath(payload.getInstanceId());

            logManager.prepare(payload.getInstanceId());

            payload = payload.putHeader(LOG_FILE_NAME, name)
                    .putHeader(LOG_FILE_PATH, path);

            return chain.process(payload);
        } catch (IOException e) {
            throw new ProcessException("Error while creating a process log file", e);
        }
    }
}
