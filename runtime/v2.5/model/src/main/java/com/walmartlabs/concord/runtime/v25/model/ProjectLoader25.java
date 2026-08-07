package com.walmartlabs.concord.runtime.v25.model;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import com.walmartlabs.concord.runtime.v25.model.parser.DefinitionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.walmartlabs.concord.process.loader.StandardRuntimeTypes.PROJECT_ROOT_FILE_NAMES;

public final class ProjectLoader25 implements ProjectLoader {
    private static final Logger log = LoggerFactory.getLogger(ProjectLoader25.class);


    private final ImportManager importManager;
    private final DefinitionParser parser;

    @Inject
    public ProjectLoader25(ImportManager importManager) {
        this(importManager, new DefinitionParser());
    }

    ProjectLoader25(ImportManager importManager, DefinitionParser parser) {
        this.importManager = importManager;
        this.parser = parser;
    }

    @Override
    public boolean supports(String runtime) {
        return Definition25.RUNTIME_TYPE.equals(runtime);
    }

    @Override
    public Result loadProject(Path workDir, String runtime, ImportsNormalizer importsNormalizer,
                              ImportsListener listener) throws Exception {
        if (!supports(runtime)) {
            throw new IllegalArgumentException("Unsupported runtime: " + runtime);
        }
        var baseDir = workDir.toRealPath();
        var root = loadRoot(baseDir);
        var snapshots = List.<Snapshot>of();
        if (root != null && !root.definition().imports().isEmpty()) {
            snapshots = importManager.process(importsNormalizer.normalize(root.definition().imports()), baseDir, listener);
        }

        var definitions = new ArrayList<Definition25>();
        for (var resource : loadResources(baseDir, root != null ? root.definition().resources() : Map.of())) {
            if (root != null && resource.equals(root.path())) {
                continue;
            }
            var definition = parser.parse(baseDir, resource);
            warnAboutExtraImports(definition, listener);
            definitions.add(definition);
        }
        if (root != null) {
            definitions.add(root.definition());
        }
        if (definitions.isEmpty()) {
            throw new IllegalStateException("Can't find any Concord process definition files in '" + baseDir + "'");
        }
        return new LoaderResult(List.copyOf(snapshots), parser.merge(definitions));
    }

    public Definition25 loadFromFile(Path path) throws IOException {
        if (Files.notExists(path)) {
            throw new IllegalStateException("Can't find Concord process definition file: " + path);
        }
        var normalizedPath = path.toAbsolutePath().normalize();
        return parser.parse(normalizedPath.getParent(), normalizedPath);
    }

    private LoadedDefinition loadRoot(Path baseDir) throws IOException {
        for (var fileName : PROJECT_ROOT_FILE_NAMES) {
            var path = baseDir.resolve(fileName);
            if (Files.isRegularFile(path)) {
                var canonicalPath = canonicalResource(baseDir, path);
                return new LoadedDefinition(canonicalPath, parser.parse(baseDir, canonicalPath));
            }
        }
        return null;
    }

    private List<Path> loadResources(Path baseDir, Map<String, Object> resources) throws IOException {
        var patterns = resources.containsKey("concord")
                ? strings(resources.get("concord"))
                : List.of("glob:concord/{**/,}{*.,}concord.{yml,yaml}");
        var result = new ArrayList<Path>();
        for (var pattern : patterns) {
            if (pattern.startsWith("glob:") || pattern.startsWith("regex:")) {
                var separator = pattern.startsWith("glob:") ? "glob:" : "regex:";
                var expression = pattern.substring(separator.length());
                var matcher = FileSystems.getDefault().getPathMatcher(separator + baseDir + "/" + expression);
                try (Stream<Path> files = Files.walk(baseDir)) {
                    for (var candidate : files.filter(Files::isRegularFile).filter(matcher::matches).toList()) {
                        result.add(canonicalResource(baseDir, candidate));
                    }
                }
            } else {
                var path = DefinitionParser.resolveContained(baseDir, pattern, "resources.concord");
                if (Files.isRegularFile(path)) {
                    result.add(canonicalResource(baseDir, path));
                }
            }
        }
        return result.stream().distinct().sorted(Comparator.naturalOrder()).toList();
    }

    private Path canonicalResource(Path baseDir, Path path) throws IOException {
        var canonicalPath = path.toRealPath();
        if (!canonicalPath.startsWith(baseDir)) {
            throw new IllegalArgumentException("Resource path escapes the project root: " + path);
        }
        return canonicalPath;
    }

    private void warnAboutExtraImports(Definition25 definition, ImportsListener listener) {
        var range = definition.importsRange();
        if (range == null) {
            return;
        }
        var diagnostic = new Diagnostic("V25_EXTRA_IMPORTS_IGNORED", Diagnostic.Severity.WARNING,
                "Imports declared outside the project root definition are ignored", range, "$.imports", null);
        if (listener instanceof ProjectLoader25Listener diagnosticListener) {
            diagnosticListener.onDiagnostic(diagnostic);
            return;
        }
        log.warn("{} at {} ({}): {}", diagnostic.code(), range, diagnostic.path(), diagnostic.message());
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Iterable<?> values)) {
            return List.of();
        }
        var result = new ArrayList<String>();
        values.forEach(item -> result.add(item.toString()));
        return result;
    }

    private record LoadedDefinition(Path path, Definition25 definition) {
    }

    private record LoaderResult(List<Snapshot> snapshots, ProcessDefinition projectDefinition) implements Result {
    }
}
