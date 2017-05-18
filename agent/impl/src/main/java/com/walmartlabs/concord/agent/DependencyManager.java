package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.common.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;

public class DependencyManager {

    private static final Logger log = LoggerFactory.getLogger(DependencyManager.class);

    private final Configuration cfg;
    private final Object mutex = new Object();

    public DependencyManager(Configuration cfg) {
        this.cfg = cfg;
    }

    public void collectDependencies(Collection<String> urls, Path dstDir) throws IOException {
        if (urls == null || urls.isEmpty()) {
            return;
        }

        Path cacheDir;
        synchronized (mutex) {
            cacheDir = cfg.getDependencyCacheDir();
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }
        }

        for (String s : urls) {
            URI uri = URI.create(s);

            String name = getLastPart(uri);
            Path src = cacheDir.resolve(name);

            // TODO striped locks?
            synchronized (mutex) {
                if (!Files.exists(src)) {
                    log.info("collectDependencies -> downloading {}...", s);
                    download(uri, src);
                }

                // TODO integrity check?
                log.info("collectDependencies -> using cached {}", src);
                if (!Files.exists(dstDir)) {
                    Files.createDirectories(dstDir);
                }

                Path dst = dstDir.resolve(name);
                Files.createSymbolicLink(dst, src);
                continue;
            }
        }
    }

    private static void download(URI uri, Path dst) throws IOException {
        try (InputStream in = uri.toURL().openStream();
             OutputStream out = Files.newOutputStream(dst, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            IOUtils.copy(in, out);
        }
    }

    private static String getLastPart(URI uri) {
        String p = uri.getPath();
        int idx = p.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < p.length()) {
            return p.substring(idx + 1);
        }
        throw new IllegalArgumentException("Invalid dependency URL: " + uri);
    }
}
