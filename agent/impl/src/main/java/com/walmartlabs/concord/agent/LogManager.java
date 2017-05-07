package com.walmartlabs.concord.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Named
@Singleton
public class LogManager {

    private static final Logger log = LoggerFactory.getLogger(LogManager.class);

    private final Configuration cfg;

    @Inject
    public LogManager(Configuration cfg) {
        this.cfg = cfg;
    }

    public void delete(String id) {
        Path f = logFile(id);
        if (Files.exists(f)) {
            try {
                Files.delete(f);
            } catch (IOException e) {
                throw new RuntimeException("Error removing a log file: " + f, e);
            }
        }
    }

    public void log(String id, String log, Object... args) {
        if (args != null && args.length > 0) {
            int last = args.length - 1;
            Object o = args[last];
            if (o instanceof Throwable) {
                StringWriter sw = new StringWriter();
                PrintWriter w = new PrintWriter(sw);
                ((Throwable) o).printStackTrace(w);
                args[last] = sw.toString();
            }
        }

        Path f = logFile(id);
        try (OutputStream out = Files.newOutputStream(f, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String s = String.format(log + "\n", args);
            out.write(s.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Error writting to a log file: " + f, e);
        }
    }

    public void log(String id, InputStream src) throws IOException {
        Path f = logFile(id);
        log.info("store ['{}'] -> storing into {}", id, f);
        try (OutputStream dst = Files.newOutputStream(f, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
             BufferedReader reader = new BufferedReader(new InputStreamReader(src))) {

            String line;
            while ((line = reader.readLine()) != null) {
                dst.write(line.getBytes());
                dst.write('\n');
                dst.flush();
            }
        }
    }

    public void touch(String id) {
        Path f = logFile(id);
        if (!Files.exists(f)) {
            try {
                Files.createFile(f);
            } catch (IOException e) {
                throw new RuntimeException("Error opening a log file: " + f, e);
            }
        }
    }

    public Path open(String id) {
        return logFile(id);
    }

    private Path logFile(String id) {
        Path baseDir = cfg.getLogDir();
        return baseDir.resolve(id + ".log");
    }
}
