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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.Resources;
import com.walmartlabs.concord.runtime.v2.parser.YamlParserV2;
import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

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

        List<Path> files = loadResources(baseDir, root != null ? root.resources() : Resources.builder().build());
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

        return new Result(snapshots, merge(definitions));
    }

    public void export(Path baseDir, Path destDir, ImportsNormalizer importsNormalizer, CopyOption... options) throws Exception {
        YamlParserV2 parser = new YamlParserV2();

        ProcessDefinition root = loadRoot(parser, baseDir);

        Resources resources = root != null ? root.resources(): Resources.builder().build();
        boolean hasImports = root != null && root.imports() != null && !root.imports().isEmpty();
        if (!hasImports) {
            copyResources(baseDir, resources, destDir, options);
            return;
        }

        Path tmpDir = null;
        try {
            tmpDir = IOUtils.createTempDir("concord-export");
            copyResources(baseDir, resources, tmpDir, options);

            Imports imports = importsNormalizer.normalize(root.imports());
            importManager.process(imports, tmpDir);

            copyResources(tmpDir, resources, destDir, options);
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

    private static List<Path> loadResources(Path baseDir, Resources resources) throws IOException {
        List<Path> result = new ArrayList<>();
        for (String pattern : resources.concord()) {
            PathMatcher pathMatcher = parsePattern(baseDir, pattern);
            if (pathMatcher != null) {
                try (Stream<Path> w = Files.walk(baseDir)) {
                    w.filter(pathMatcher::matches).forEach(result::add);
                }
            } else {
                Path path = Paths.get(concat(baseDir, pattern.trim()));
                if (Files.exists(path)) {
                    result.add(path);
                }
            }
        }
        return result;
    }

    private static PathMatcher parsePattern(Path baseDir, String pattern) {
        String normalizedPattern = null;

        pattern = pattern.trim();

        if (pattern.startsWith("glob:")) {
            normalizedPattern = "glob:" + concat(baseDir, pattern.substring("glob:".length()));
        } else if (pattern.startsWith("regex:")) {
            normalizedPattern = "regex:" + concat(baseDir, pattern.substring("regex:".length()));
        }

        if (normalizedPattern != null) {
            return FileSystems.getDefault().getPathMatcher(normalizedPattern);
        }

        return null;
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

    private static String concat(Path path, String str) {
        String separator = "/";
        if (str.startsWith("/")) {
            separator = "";
        }
        return path.toAbsolutePath() + separator + str;
    }

    private static void copyResources(Path baseDir, Resources resources, Path destDir, CopyOption... options) throws IOException {
        List<Path> files = loadResources(baseDir, resources);
        for (String fileName : Constants.Files.PROJECT_ROOT_FILE_NAMES) {
            Path p = baseDir.resolve(fileName);
            files.add(p);
        }
        copy(new HashSet<>(files), baseDir, destDir, options);
    }

    private static void copy(Set<Path> files, Path baseDir, Path dest, CopyOption... options) throws IOException {
        for (Path f : files) {
            if (Files.notExists(f)) {
                continue;
            }

            Path src = baseDir.relativize(f);
            Path dst = dest.resolve(src);
            Path dstParent = dst.getParent();
            if (dstParent != null && Files.notExists(dstParent)) {
                Files.createDirectories(dstParent);
            }
            Files.copy(f, dst, options);
        }
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
