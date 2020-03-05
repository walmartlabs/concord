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

import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.parser.YamlParserV2;
import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

// TODO rename to ProcessDefinitionLoader?
public class ProjectLoaderV2 {

    private final ImportManager importManager;

    public ProjectLoaderV2(ImportManager importManager) {
        this.importManager = importManager;
    }

    public Result load(Path baseDir, ImportsNormalizer importsNormalizer) throws Exception {
        YamlParserV2 parser = new YamlParserV2();

        ProcessDefinition root = loadRoot(parser, baseDir);
        List<Snapshot> snapshots = Collections.emptyList();
        if (root != null) {
            Imports imports = importsNormalizer.normalize(root.imports());
            snapshots = importManager.process(imports, baseDir);
        }

        // TODO load resource definitions
        // TODO: log file names with process definitions

        // ./concord/**/*.concord.yml
        PathMatcher pathMatcher = FileSystems.getDefault()
                .getPathMatcher("glob:" + baseDir.toAbsolutePath() + "/concord/{**/,}{*.,}concord.yml");

        List<Path> files = new ArrayList<>();
        try(Stream<Path> w = Files.walk(baseDir)) {
            w.filter(pathMatcher::matches).forEach(files::add);
        }

        Collections.sort(files);

        List<ProcessDefinition> definitions = new ArrayList<>();
        for (Path p : files) {
            definitions.add(parser.parse(baseDir, p));
        }

        if (root != null) {
            definitions.add(root);
        }

        if (definitions.isEmpty()) {
            throw new IllegalStateException("Can't find any Concord process definition files in '" + baseDir + "'");
        }

        // ./concord/forms/**/index.html

        return new Result(snapshots, merge(definitions));
    }

    private ProcessDefinition loadRoot(YamlParserV2 parser, Path baseDir) throws IOException {
        for (String fileName : Constants.Files.PROJECT_ROOT_FILE_NAMES) {
            Path p = baseDir.resolve(fileName);
            if (Files.exists(p)) {
                return parser.parse(baseDir, p);
            }
        }
        return null;
    }

    public Result loadFromFile(Path path) throws IOException {
        YamlParserV2 parser = new YamlParserV2();

        if (Files.notExists(path)) {
            throw new IllegalStateException("Can't find Concord process definition file: " + path);
        }

        return new Result(Collections.emptyList(), parser.parse(path.getParent(), path));
    }

    private static ProcessDefinition merge(List<ProcessDefinition> definitions) {
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("Definitions is empty");
        }

        ProcessDefinition result = definitions.get(0);
        for (int i = 1; i < definitions.size(); i++) {
            ProcessDefinition pd = definitions.get(i);

            result = ProcessDefinition.merge(result, pd);
        }
        return result;
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
