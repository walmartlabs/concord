package com.walmartlabs.concord.runtime.v1;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.process.loader.ImportsNormalizer;
import com.walmartlabs.concord.process.loader.ProjectLoader;
import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.runtime.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v1.wrapper.ProcessDefinitionV1;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.List;

import static com.walmartlabs.concord.process.loader.StandardRuntimeTypes.CONCORD_V1_RUNTIME_TYPE;
import static java.util.Objects.requireNonNull;

public class ProjectLoaderV1 implements ProjectLoader {

    private final com.walmartlabs.concord.project.ProjectLoader v1;

    @Inject
    public ProjectLoaderV1(ImportManager importManager) {
        this.v1 = new com.walmartlabs.concord.project.ProjectLoader(requireNonNull(importManager));
    }

    @Override
    public boolean supports(String runtime) {
        return CONCORD_V1_RUNTIME_TYPE.equals(runtime);
    }

    @Override
    public Result loadProject(Path workDir, String runtime, ImportsNormalizer importsNormalizer, ImportsListener listener) throws Exception {
        var v1Result = v1.loadProject(workDir, importsNormalizer::normalize, listener);
        return toCommonResultType(v1Result);
    }

    private static Result toCommonResultType(com.walmartlabs.concord.project.ProjectLoader.Result r) {
        var snapshots = r.getSnapshots();
        var pd = new ProcessDefinitionV1(r.getProjectDefinition());

        return new Result() {
            @Override
            public List<Snapshot> snapshots() {
                return snapshots;
            }

            @Override
            public ProcessDefinition projectDefinition() {
                return pd;
            }
        };
    }
}
