package com.walmartlabs.concord.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

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
