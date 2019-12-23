package com.walmartlabs.concord.server.process.loader;

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

import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.server.process.loader.model.ProjectDefinition;
import com.walmartlabs.concord.server.process.loader.v1.ProjectDefinitionV1;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Handles loading of v1 and (future) v2 project definitions.
 */
@Named
public class ProjectLoader {

    private final com.walmartlabs.concord.project.ProjectLoader v1;

    @Inject
    public ProjectLoader(ImportManager importManager) {
        this.v1 = new com.walmartlabs.concord.project.ProjectLoader(importManager);
    }

    public Result loadProject(Path workDir, ImportsNormalizer importsNormalizer, Map<String, Object> overrides) throws Exception {
        return toResult(v1.loadProject(workDir, importsNormalizer::normalize, overrides));
    }

    private static Result toResult(com.walmartlabs.concord.project.ProjectLoader.Result r) {
        List<Snapshot> snapshots = r.getSnapshots();
        ProjectDefinition pd = new ProjectDefinitionV1(r.getProjectDefinition());

        return new Result() {
            @Override
            public List<Snapshot> snapshots() {
                return snapshots;
            }

            @Override
            public ProjectDefinition projectDefinition() {
                return pd;
            }
        };
    }

    public static boolean isConcordFileExists(Path path) {
        return com.walmartlabs.concord.project.ProjectLoader.isConcordFileExists(path);
    }

    public interface Result {

        List<Snapshot> snapshots(); // TODO can it be a collection?

        ProjectDefinition projectDefinition();
    }
}
