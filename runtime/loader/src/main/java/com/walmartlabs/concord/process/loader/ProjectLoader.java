package com.walmartlabs.concord.process.loader;

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

import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.runtime.model.ProcessDefinition;
import com.walmartlabs.concord.repository.Snapshot;

import java.nio.file.Path;
import java.util.List;

public interface ProjectLoader {

    /**
     * Loads the directory as a project. The runtime is detected based on the directory's content.
     */
    Result loadProject(Path workDir, ImportsNormalizer importsNormalizer, ImportsListener listener) throws Exception;

    /**
     * Loads the directory as a project of the specified runtime.
     */
    Result loadProject(Path workDir, String runtime, ImportsNormalizer importsNormalizer, ImportsListener listener) throws Exception;

    interface Result {

        List<Snapshot> snapshots();

        ProcessDefinition projectDefinition();
    }
}
