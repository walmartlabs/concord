package com.walmartlabs.concord.server;

import com.walmartlabs.concord.server.cfg.LogStoreConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Named
@Singleton
public class LogManager {

    private static final Logger log = LoggerFactory.getLogger(LogManager.class);

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final LogStoreConfiguration cfg;

    @Inject
    public LogManager(LogStoreConfiguration cfg) throws IOException {
        this.cfg = cfg;

        init();
    }

    private void init() throws IOException {
        Path baseDir = cfg.getBaseDir();

        Files.createDirectories(baseDir);

        log.info("init ['{}'] -> success", baseDir);
    }

    public void debug(String id, String log, Object... args) {
        log_(id, LogLevel.DEBUG, log, args);
    }

    public void info(String id, String log, Object... args) {
        log_(id, LogLevel.INFO, log, args);
    }

    public void warn(String id, String log, Object... args) {
        log_(id, LogLevel.WARN, log, args);
    }

    public void error(String id, String log, Object... args) {
        log_(id, LogLevel.ERROR, log, args);
    }

    public void log(String id, String msg) throws IOException {
        log(id, msg.getBytes());
    }

    public synchronized void log(String id, byte[] msg) throws IOException {
        Path f = getPath(id);
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(f, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            out.write(msg);
            out.flush();
        }
    }

    public String getFileName(String id) {
        return id + ".log";
    }

    public Path getPath(String id) {
        Path baseDir = cfg.getBaseDir();
        return baseDir.resolve(getFileName(id));
    }

    public void prepare(String id) throws IOException {
        Path path = getPath(id);
        if (!Files.exists(path)) {
            Files.createFile(path);
        }
    }

    private void log_(String id, LogLevel level, String msg, Object... args) {
        try {
            log(id, formatMessage(level, msg, args));
        } catch (IOException e) {
            log.error("log ['{}', {}] -> error", id, level, e);
        }
    }

    private static String formatMessage(LogLevel level, String log, Object... args) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        FormattingTuple m = MessageFormatter.arrayFormat(log, args);
        if(m.getThrowable() != null) {
            return String.format("%s [%-5s] %s%n%s%n", timestamp, level.name(), m.getMessage(),
                    formatException(m.getThrowable()));
        } else {
            return String.format("%s [%-5s] %s%n", timestamp, level.name(), m.getMessage());
        }
    }

    private static String formatException(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private enum LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }
}
