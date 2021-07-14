package com.walmartlabs.concord.process.loader;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.process.loader.model.ProcessDefinition;
import com.walmartlabs.concord.process.loader.v1.ProcessDefinitionV1;
import com.walmartlabs.concord.process.loader.v2.ProcessDefinitionV2;
import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.sdk.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Handles loading of v1 and (future) v2 project definitions.
 */
@Named
public class ProjectLoader {

    private static final Logger log = LoggerFactory.getLogger(ProjectLoader.class);

    private final com.walmartlabs.concord.project.ProjectLoader v1;
    private final com.walmartlabs.concord.runtime.v2.ProjectLoaderV2 v2;

    @Inject
    public ProjectLoader(ImportManager importManager) {
        this.v1 = new com.walmartlabs.concord.project.ProjectLoader(importManager);
        this.v2 = new com.walmartlabs.concord.runtime.v2.ProjectLoaderV2(importManager);
    }

    public Result loadProject(Path workDir, ImportsNormalizer importsNormalizer, ImportsListener listener) throws Exception {
        String runtime = getRuntimeType(workDir, "concord-v1"); // TODO constants
        return loadProject(workDir, runtime, importsNormalizer, listener);
    }

    public Result loadProject(Path workDir, String runtime, ImportsNormalizer importsNormalizer, ImportsListener listener) throws Exception {
        if ("concord-v2".equals(runtime)) { // TODO constants
            return toResult(v2.load(workDir, importsNormalizer::normalize, listener));
        }

        return toResult(v1.loadProject(workDir, importsNormalizer::normalize, listener));
    }

    private static Result toResult(com.walmartlabs.concord.project.ProjectLoader.Result r) {
        List<Snapshot> snapshots = r.getSnapshots();
        ProcessDefinition pd = new ProcessDefinitionV1(r.getProjectDefinition());

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

    private static Result toResult(com.walmartlabs.concord.runtime.v2.ProjectLoaderV2.Result r) {
        List<Snapshot> snapshots = r.getSnapshots();
        ProcessDefinition pd = new ProcessDefinitionV2(r.getProjectDefinition());

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

    public static boolean isConcordFileExists(Path path) {
        // TODO v2
        return com.walmartlabs.concord.project.ProjectLoader.isConcordFileExists(path);
    }

    public static String getRuntimeType(Path workDir, String defaultType) throws IOException {
        for (String filename : Constants.Files.PROJECT_ROOT_FILE_NAMES) {
            Path src = workDir.resolve(filename);
            if (Files.exists(src)) {
                ObjectMapper om = new ObjectMapper(new YAMLFactory());
                try (InputStream in = Files.newInputStream(src)) {
                    JsonNode n = om.readTree(in);

                    n = n.get(Constants.Request.CONFIGURATION_KEY);
                    if (n == null) {
                        continue;
                    }

                    n = n.get(Constants.Request.RUNTIME_KEY);
                    if (n == null) {
                        continue;
                    }

                    // TODO constants
                    String s = n.textValue();
                    if (s != null) {
                        return s;
                    }
                }
            }
        }

        return defaultType;
    }

    public interface Result {

        List<Snapshot> snapshots(); // TODO can it be a collection?

        ProcessDefinition projectDefinition();
    }
}
