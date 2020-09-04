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

import com.walmartlabs.concord.imports.Import.GitDefinition;
import com.walmartlabs.concord.repository.Snapshot;

import java.nio.file.Path;

public class RepositoryProcessor implements ImportProcessor<GitDefinition> {

    private final RepositoryExporter repositoryExporter;

    public RepositoryProcessor(RepositoryExporter repositoryExporter) {
        this.repositoryExporter = repositoryExporter;
    }

    @Override
    public String type() {
        return GitDefinition.TYPE;
    }

    @Override
    public Snapshot process(GitDefinition entry, Path workDir) throws Exception {
        return repositoryExporter.export(entry, workDir);
    }
}
