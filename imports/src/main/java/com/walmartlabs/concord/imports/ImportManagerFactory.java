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

import com.walmartlabs.concord.dependencymanager.DependencyManager;

import java.util.ArrayList;
import java.util.List;

public class ImportManagerFactory {

    private final DependencyManager dependencyManager;
    private final RepositoryExporter repositoryExporter;

    public ImportManagerFactory(DependencyManager dependencyManager, RepositoryExporter repositoryExporter) {
        this.dependencyManager = dependencyManager;
        this.repositoryExporter = repositoryExporter;
    }

    public ImportManager create() {
        List<ImportProcessor> processors = new ArrayList<>();
        processors.add(new RepositoryProcessor(repositoryExporter));
        processors.add(new MvnProcessor(dependencyManager));
        return new ImportManager(processors);
    }
}
