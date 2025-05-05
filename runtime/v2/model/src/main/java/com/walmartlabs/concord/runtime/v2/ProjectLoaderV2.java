package com.walmartlabs.concord.runtime.v2;

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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.ResourceUtils;
import com.walmartlabs.concord.imports.Import;
import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.parser.YamlParserV2;
import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.walmartlabs.concord.runtime.v2.model.Resources.DEFAULT_CONCORD_RESOURCES;

public class ProjectLoaderV2 {

    private final ImportManager importManager;

    public ProjectLoaderV2(ImportManager importManager) {
        this.importManager = importManager;
    }

    public Result load(Path baseDir, ImportsNormalizer importsNormalizer, ImportsListener listener) throws Exception {
        YamlParserV2 parser = new YamlParserV2();

        // load the initial ProcessDefinition from the root concord.yml file
        // it will be used to determine whether we need to load other resources (e.g. imports)
        ProcessDefinition root = loadRoot(parser, baseDir);

        List<Snapshot> snapshots = Collections.emptyList();
        if (root != null) {
            Imports imports = importsNormalizer.normalize(root.imports());
            snapshots = importManager.process(imports, baseDir, listener);
        }

        List<Path> files = ResourceUtils.findResources(baseDir, root != null ? root.resources().concord() : DEFAULT_CONCORD_RESOURCES);
        Collections.sort(files);

        List<ProcessDefinition> definitions = new ArrayList<>();
        for (Path p : files) {
            ProcessDefinition pd = parser.parse(baseDir, p);
            definitions.add(pd);
        }

        if (root != null) {
            definitions.add(root);
        }

        if (definitions.isEmpty()) {
            throw new IllegalStateException("Can't find any Concord process definition files in '" + baseDir + "'");
        }

        return new Result(snapshots, merge(definitions));
    }

    public void export(Path baseDir, Path destDir, ImportsNormalizer importsNormalizer, ImportsListener listener, CopyOption... options) throws Exception {
        YamlParserV2 parser = new YamlParserV2();

        ProcessDefinition root = loadRoot(parser, baseDir);

        Resources resources = root != null ? root.resources() : Resources.builder().build();
        boolean hasImports = root != null && root.imports() != null && !root.imports().isEmpty();
        if (!hasImports) {
            ResourceUtils.copyResources(baseDir, resources.concord(), destDir, options);
            return;
        }

        Path tmpDir = null;
        try {
            tmpDir = IOUtils.createTempDir("concord-export");
            ResourceUtils.copyResources(baseDir, resources.concord(), tmpDir, options);

            Imports imports = importsNormalizer.normalize(root.imports());
            importManager.process(imports, tmpDir, listener);

            ResourceUtils.copyResources(tmpDir, resources.concord(), destDir, options);
        } finally {
            if (tmpDir != null) {
                IOUtils.deleteRecursively(tmpDir);
            }
        }
    }

    private ProcessDefinition loadRoot(YamlParserV2 parser, Path baseDir) throws IOException {
        for (String fileName : Constants.Files.PROJECT_ROOT_FILE_NAMES) {
            Path p = baseDir.resolve(fileName);
            if (Files.exists(p)) {
                ProcessDefinition result = parser.parse(baseDir, p);
                return result;
            }
        }
        return null;
    }

    public Result loadFromFile(Path path) throws IOException {
        YamlParserV2 parser = new YamlParserV2();

        if (Files.notExists(path)) {
            throw new IllegalStateException("Can't find Concord process definition file: " + path);
        }
        ProcessDefinition pd = parser.parse(path.getParent(), path);
        return new Result(List.of(), pd);
    }

    private static ProcessDefinition merge(List<ProcessDefinition> definitions) {
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("Definitions is empty");
        }

        Map<String, Flow> flows = new LinkedHashMap<>();
        Map<String, Profile> profiles = new LinkedHashMap<>();
        List<Trigger> triggers = new ArrayList<>();
        List<Import> imports = new ArrayList<>();
        Map<String, Form> forms = new LinkedHashMap<>();
        Set<String> resources = new HashSet<>();
        Set<String> dependencies = new HashSet<>();
        Set<String> extraDependencies = new HashSet<>();
        Map<String, Object> arguments = new LinkedHashMap<>();

        for (ProcessDefinition pd : definitions) {
            flows.putAll(pd.flows());
            profiles.putAll(pd.profiles());
            triggers.addAll(pd.triggers());
            imports.addAll(pd.imports().items());
            forms.putAll(pd.forms());
            resources.addAll(pd.resources().concord());
            dependencies.addAll(pd.configuration().dependencies());
            extraDependencies.addAll(pd.configuration().extraDependencies());
            arguments = ConfigurationUtils.deepMerge(arguments, pd.configuration().arguments());
        }

        ProcessDefinition root = definitions.get(definitions.size() - 1);

        return ProcessDefinition.builder().from(root)
                .configuration(ProcessDefinitionConfiguration.builder().from(root.configuration())
                        .dependencies(dependencies)
                        .extraDependencies(extraDependencies)
                        .arguments(arguments)
                        .build())
                .flows(flows)
                .profiles(profiles)
                .triggers(triggers)
                .imports(Imports.of(imports))
                .forms(forms)
                .resources(Resources.builder().concord(resources).build())
                .build();
    }

    public static class Result {

        private final List<Snapshot> snapshots;
        private final ProcessDefinition projectDefinition;

        public Result(List<Snapshot> snapshots, ProcessDefinition projectDefinition) {
            this.snapshots = snapshots;
            this.projectDefinition = projectDefinition;
        }

        public List<Snapshot> getSnapshots() {
            return snapshots;
        }

        public ProcessDefinition getProjectDefinition() {
            return projectDefinition;
        }
    }
}
