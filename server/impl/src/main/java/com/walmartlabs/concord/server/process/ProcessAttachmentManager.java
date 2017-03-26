package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.cfg.AttachmentStoreConfiguration;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;

@Named
public class ProcessAttachmentManager {

    private final AttachmentStoreConfiguration cfg;

    @Inject
    public ProcessAttachmentManager(AttachmentStoreConfiguration cfg) {
        this.cfg = cfg;
    }

    private Path resolve(String instanceId) {
        return cfg.getBaseDir().resolve(instanceId);
    }

    public void store(String instanceId, InputStream src) throws IOException {
        Path p = resolve(instanceId);
        if (Files.exists(p)) {
            IOUtils.deleteRecursively(p);
        }

        try (ZipInputStream zip = new ZipInputStream(src)) {
            IOUtils.unzip(zip, p);
        }
    }

    public boolean contains(String instanceId, String name) {
        Path p = resolve(instanceId).resolve(normalizeName(name));
        return Files.exists(p);
    }

    public Path get(String instanceId, String name) {
        Path p = resolve(instanceId).resolve(normalizeName(name));
        if (!Files.exists(p)) {
            return null;
        }
        return p;
    }

    public void delete(String instanceId, String name) throws IOException {
        Path p = resolve(instanceId).resolve(normalizeName(name));
        if (Files.exists(p)) {
            IOUtils.deleteRecursively(p);
        }
    }

    private static String normalizeName(String name) {
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        return name;
    }
}
