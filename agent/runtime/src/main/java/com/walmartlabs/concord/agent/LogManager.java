package com.walmartlabs.concord.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.*;
import java.nio.file.Path;

@Named
@Singleton
public class LogManager {

    private static final Logger log = LoggerFactory.getLogger(LogManager.class);

    private final Configuration cfg;

    @Inject
    public LogManager(Configuration cfg) {
        this.cfg = cfg;
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

        File f = logFile(id);
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f, true))) {
            String s = String.format(log + "\n", args);
            out.write(s.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Error writting to a log file: " + f, e);
        }
    }

    public void store(String id, InputStream src) {
        File f = logFile(id);
        log.info("store ['{}'] -> storing into {}", id, f.getAbsolutePath());
        try (OutputStream dst = new BufferedOutputStream(new FileOutputStream(f, true));
             BufferedReader reader = new BufferedReader(new InputStreamReader(src))) {

            String line;
            while ((line = reader.readLine()) != null) {
                dst.write(line.getBytes());
                dst.write('\n');
                dst.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error writing to a log file: " + f, e);
        }
    }

    public File open(String id) {
        return logFile(id);
    }

    private File logFile(String id) {
        Path baseDir = cfg.getLogStore();

        File f = baseDir.toFile();
        if (!f.exists()) {
            if (!f.mkdirs()) {
                throw new RuntimeException("Can't create a log directory: " + f.getAbsolutePath());
            }
        }

        return baseDir.resolve(id + ".log").toFile();
    }
}
