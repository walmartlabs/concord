package com.walmartlabs.concord.common;

import com.walmartlabs.concord.common.cfg.UnzipLimits;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Path;

public class ZipService {

    private final UnzipLimits unzipLimits;

    @Inject
    public ZipService(UnzipLimits unzipLimits) {
        this.unzipLimits = unzipLimits;
    }

    public void unzip(InputStream in, Path targetDir, CopyOption... options) throws IOException {
        ZipUtils.unzip(in, targetDir, unzipLimits, options);
    }

    public void unzip(Path in, Path targetDir, CopyOption... options) throws IOException {
        ZipUtils.unzip(in, targetDir, unzipLimits, options);
    }

    public void unzip(Path in, Path targetDir, boolean skipExisting, CopyOption... options) throws IOException {
        ZipUtils.unzip(in, targetDir, skipExisting, unzipLimits, options);
    }

    public void unzip(Path in, Path targetDir, boolean skipExisting, FileVisitor visitor, CopyOption... options) throws IOException {
        ZipUtils.unzip(in, targetDir, skipExisting, visitor, unzipLimits, options);
    }

}
