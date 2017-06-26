package com.walmartlabs.concord.common;

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

    private final Path cacheDir;
    private final Object mutex = new Object();

    public DependencyManager(Path cacheDir) throws IOException {
        this.cacheDir = cacheDir;
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }
    }

    public void collectDependencies(Collection<String> urls, Path dstDir) throws IOException {
        if (urls == null || urls.isEmpty()) {
            return;
        }

        for (String s : urls) {
            URI uri = URI.create(s);
            String name = getLastPart(uri);
            Path cachedPath = resolve(uri);

            log.info("collectDependencies -> using cached {}", cachedPath);
            if (!Files.exists(dstDir)) {
                Files.createDirectories(dstDir);
            }

            Path payloadPath = dstDir.resolve(name);
            Files.createSymbolicLink(payloadPath, cachedPath);
            continue;
        }
    }

    public Path resolve(URI uri) throws IOException {
        boolean skipCache = shouldSkipCache(uri);
        String name = getLastPart(uri);

        Path cachedPath = cacheDir.resolve(name);

        synchronized (mutex) {
            if (skipCache || !Files.exists(cachedPath)) {
                log.info("resolve -> downloading {}...", uri);
                download(uri, cachedPath);
            }

            return cachedPath;
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

    private static boolean shouldSkipCache(URI u) {
        return "file".equalsIgnoreCase(u.getScheme()) || u.getPath().contains("SNAPSHOT");
    }
}
