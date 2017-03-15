package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.cfg.AttachmentStoreConfiguration;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Named
public class ProcessAttachmentManager {

    private final AttachmentStoreConfiguration cfg;

    @Inject
    public ProcessAttachmentManager(AttachmentStoreConfiguration cfg) {
        this.cfg = cfg;
    }

    private Path resolve(String instanceId) {
        return cfg.getBaseDir().resolve(instanceId + ".zip");
    }

    public void store(String instanceId, InputStream src) throws IOException {
        Path p = resolve(instanceId);
        // TODO replace?
        Files.copy(src, p, StandardCopyOption.REPLACE_EXISTING);
    }

    public Path extract(String instanceId, String name) throws IOException {
        Path p = resolve(instanceId);
        if (!Files.exists(p)) {
            return null;
        }

        if (name.startsWith("/")) {
            name = name.substring(1);
        }

        boolean dirMode = name.endsWith("/");

        if (dirMode) {
            return extractDir(p, name);
        } else {
            return extractFile(p, name);
        }
    }

    private static Path extractFile(Path src, String name) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(src))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (name.equals(e.getName())) {
                    if (e.isDirectory()) {
                        throw new IOException("Downloadable attachment must be a file: " + name);
                    }

                    Path tmpFile = Files.createTempFile("attachment", ".data");
                    try (OutputStream out = Files.newOutputStream(tmpFile)) {
                        IOUtils.copy(zip, out);
                    }

                    return tmpFile;
                }
            }
        }

        return null;
    }

    private static Path extractDir(Path src, String name) throws IOException {
        Path tmpDir = null;

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(src))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String n = e.getName();
                if (!n.startsWith(name)) {
                    continue;
                }

                if (tmpDir == null) {
                    tmpDir = Files.createTempDirectory("attachment");
                }

                String nn = n.substring(name.length());
                Path dst = tmpDir.resolve(nn);
                if (e.isDirectory()) {
                    Files.createDirectories(dst);
                } else {
                    Path parent = dst.getParent();
                    if (!Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }

                    try (OutputStream out = Files.newOutputStream(dst)) {
                        IOUtils.copy(zip, out);
                    }
                }
            }
        }

        return tmpDir;
    }
}
