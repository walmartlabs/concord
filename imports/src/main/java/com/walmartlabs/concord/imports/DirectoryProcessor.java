package com.walmartlabs.concord.imports;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.imports.Import.DirectoryDefinition;
import com.walmartlabs.concord.repository.LastModifiedSnapshot;
import com.walmartlabs.concord.repository.Snapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

public class DirectoryProcessor implements ImportProcessor<DirectoryDefinition> {

    @Override
    public String type() {
        return DirectoryDefinition.TYPE;
    }

    @Override
    public Snapshot process(DirectoryDefinition importEntry, Path workDir) throws Exception {
        String entrySrc = importEntry.src();

        Path src;
        if (entrySrc.startsWith("/")) {
            src = Paths.get(entrySrc);
        } else {
            src = workDir.resolve(entrySrc).normalize();
        }

        if (!Files.exists(src) || !Files.isDirectory(src)) {
            throw new IllegalArgumentException("Can't import '" + src + "': the specified path doesn't exist or not a directory.");
        }

        if (workDir.startsWith(src)) {
            throw new IllegalArgumentException("Only external directories are allowed in imports. " +
                    "To include resources from directories located in the process' working directory use the 'resources' configuration block.");
        }

        Path dest = workDir;

        String entryDest = importEntry.dest();
        if (entryDest != null) {
            dest = workDir.resolve(entryDest);
        }

        if (!Files.exists(dest)) {
            Files.createDirectories(dest);
        }

        LastModifiedSnapshot snapshot = new LastModifiedSnapshot();
        PathUtils.copy(src, dest, Collections.emptyList(), snapshot, StandardCopyOption.REPLACE_EXISTING);
        return snapshot;
    }
}
