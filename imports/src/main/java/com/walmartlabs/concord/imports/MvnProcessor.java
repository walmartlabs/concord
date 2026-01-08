package com.walmartlabs.concord.imports;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.common.ZipUtils;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.imports.Import.MvnDefinition;
import com.walmartlabs.concord.repository.LastModifiedSnapshot;
import com.walmartlabs.concord.repository.Snapshot;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class MvnProcessor implements ImportProcessor<MvnDefinition> {

    private final DependencyManager dependencyManager;

    public MvnProcessor(DependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
    }

    @Override
    public String type() {
        return MvnDefinition.TYPE;
    }

    @Override
    public Snapshot process(MvnDefinition entry, Path workDir) throws Exception {
        URI uri = new URI(entry.url());
        Path dependencyPath = dependencyManager.resolveSingle(uri).getPath();
        return extract(entry, workDir, dependencyPath);
    }

    private Snapshot extract(MvnDefinition entry, Path workDir, Path archivePath) throws IOException {
        Path dest = workDir;
        if (entry.dest() != null) {
            dest = dest.resolve(entry.dest());
        }

        LastModifiedSnapshot snapshot = new LastModifiedSnapshot();
        ZipUtils.unzip(archivePath, dest, false, snapshot, StandardCopyOption.REPLACE_EXISTING);
        return snapshot;
    }
}
