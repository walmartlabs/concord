package com.walmartlabs.concord.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.project.model.Profile;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.model.Resources;
import com.walmartlabs.concord.project.model.Trigger;
import com.walmartlabs.concord.project.yaml.*;
import com.walmartlabs.concord.project.yaml.model.*;
import com.walmartlabs.concord.project.yaml.validator.Validator;
import com.walmartlabs.concord.project.yaml.validator.ValidatorContext;
import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static com.walmartlabs.concord.process.loader.StandardRuntimeTypes.PROJECT_ROOT_FILE_NAMES;

public class ProjectLoader {

    private final ImportManager importManager;
    private final YamlParser parser = new YamlParser();

    public ProjectLoader(ImportManager importManager) {
        this.importManager = importManager;
    }

    /**
     * Loads the project definition using the supplied stream. The stream is expected
     * to contain the root project file (concord.yml).
     */
    public Result loadProject(InputStream in) throws Exception {
        ProjectDefinitionBuilder b = new ProjectDefinitionBuilder(parser);
        b.loadDefinitions(in);
        return new Result(Collections.emptyList(), b.build());
    }

    /**
     * Loads the project definition using the supplied path. Processes the configured
     * imports, templates and extra directories with project files.
     *
     * @param workDir       the directory containing the project files. If {@code imports} are
     *                      configured, the directory will be used as a target for repository
     *                      checkouts.
     */
    public Result loadProject(Path workDir, ImportsNormalizer importsNormalizer, ImportsListener listener) throws Exception {
        workDir = workDir.normalize().toAbsolutePath();

        ProjectDefinition initial = initialLoad(workDir);
        Resources resources = initial.getResources();

        Imports imports = importsNormalizer.normalize(initial.getImports());
        List<Snapshot> snapshots = importManager.process(imports, workDir, listener);

        ProjectDefinitionBuilder b = new ProjectDefinitionBuilder(parser);

        List<String> projectPaths = resources.getProjectFilePaths();
        if (projectPaths != null) {
            for (String n : projectPaths) {
                Path p = assertLocal(workDir, workDir.resolve(n));
                if (Files.exists(p)) {
                    b.addProjects(p);
                }
            }
        }

        for (String n : PROJECT_ROOT_FILE_NAMES) {
            Path p = assertLocal(workDir, workDir.resolve(n));
            if (Files.exists(p)) {
                b.addProjectFile(workDir, p);
                break;
            }
        }

        List<String> definitionPaths = resources.getDefinitionPaths();
        if (definitionPaths != null) {
            for (String n : definitionPaths) {
                Path p = assertLocal(workDir, workDir.resolve(n));
                if (Files.exists(p)) {
                    b.addDefinitions(p);
                }
            }
        }

        List<String> profilesPaths = resources.getProfilesPaths();
        if (profilesPaths != null) {
            for (String n : profilesPaths) {
                Path p = assertLocal(workDir, workDir.resolve(n));
                if (Files.exists(p)) {
                    b.addProfiles(p);
                }
            }
        }

        ProjectDefinition pd = b.build();

        // save the normalized imports, so the exact same workDir structure
        // can be re-created later (e.g. by the Agent)
        pd = new ProjectDefinition(pd, imports);

        return new Result(snapshots, pd);
    }

    /**
     * Performs the initial load of the project files. Loads only the root concord.yml
     * (if available), doesn't process imports, templates, etc.
     */
    private ProjectDefinition initialLoad(Path baseDir) throws IOException {
        ProjectDefinitionBuilder b = new ProjectDefinitionBuilder(parser);

        for (String n : PROJECT_ROOT_FILE_NAMES) {
            Path p = baseDir.resolve(n);
            if (Files.exists(p)) {
                b.addProjectFile(baseDir, p);
                break;
            }
        }

        return b.build();
    }

    private static Path assertLocal(Path baseDir, Path p) throws IOException {
        if (!p.normalize().toAbsolutePath().startsWith(baseDir)) {
            throw new IOException("Invalid resource path, points outside of the base directory: " + p);
        }

        return p;
    }

    private static class ProjectDefinitionBuilder {
        private ValidatorContext validatorContext = new ValidatorContext();

        private final YamlParser parser;

        private Map<String, ProcessDefinition> flows;
        private Set<String> publicFlows;
        private Map<String, FormDefinition> forms;
        private Map<String, Profile> profiles;
        private List<ProjectDefinition> projectDefinitions;

        private ProjectDefinitionBuilder(YamlParser parser) {
            this.parser = parser;
        }

        public void addProjectFile(Path baseDir, Path file) throws IOException {
            YamlProject yml = parser.parseProject(baseDir, file);
            if (yml == null) {
                throw new IOException("Empty project definition: " + file);
            }

            Validator.validate(validatorContext, yml);

            ProjectDefinition pd = YamlProjectConverter.convert(yml);

            if (projectDefinitions == null) {
                projectDefinitions = new ArrayList<>();
            }
            projectDefinitions.add(pd);

        }

        public void addDefinitions(Path path) throws IOException {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!isYaml(file)) {
                        return FileVisitResult.CONTINUE;
                    }

                    loadDefinitions(path, file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        public void addProfiles(Path path) throws IOException {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!isYaml(file)) {
                        return FileVisitResult.CONTINUE;
                    }

                    loadProfiles(path, file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        public void addProjects(Path path) throws IOException {
            List<Path> files = new ArrayList<>();

            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isYaml(file)) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            Collections.sort(files);

            for (Path f : files) {
                addProjectFile(path, f);
            }
        }

        private void loadDefinitions(Path baseDir, Path file) throws IOException {
            YamlDefinitionFile df = parser.parseDefinitionFile(baseDir, file);
            loadDefinitions(df);
        }

        private void loadDefinitions(InputStream in) throws IOException {
            YamlDefinitionFile df = parser.parseDefinitionFile(in);
            loadDefinitions(df);
        }

        private void loadDefinitions(YamlDefinitionFile df) throws YamlConverterException {
            if (df == null) {
                return;
            }

            Map<String, YamlDefinition> m = df.getDefinitions();
            if (m != null) {
                for (Map.Entry<String, YamlDefinition> e : m.entrySet()) {
                    String k = e.getKey();
                    YamlDefinition v = e.getValue();

                    if (v instanceof YamlProcessDefinition) {
                        if (flows == null) {
                            flows = new HashMap<>();
                        }

                        Validator.validate(validatorContext, (YamlProcessDefinition) v);

                        flows.put(k, YamlProcessConverter.convert((YamlProcessDefinition) v));
                    } else if (v instanceof YamlFormDefinition) {
                        if (forms == null) {
                            forms = new HashMap<>();
                        }
                        forms.put(k, YamlFormConverter.convert((YamlFormDefinition) v));
                    }
                }
            }
        }

        private void loadProfiles(Path baseDir, Path file) throws IOException {
            if (!isYaml(file)) {
                return;
            }

            YamlProfileFile pf = parser.parseProfileFile(baseDir, file);
            loadProfiles(pf);
        }

        private void loadProfiles(YamlProfileFile pf) throws YamlConverterException {
            if (pf == null) {
                return;
            }

            Map<String, YamlProfile> m = pf.getProfiles();

            for (Map.Entry<String, YamlProfile> e : m.entrySet()) {
                String k = e.getKey();
                YamlProfile v = e.getValue();

                Profile p = YamlProjectConverter.convert(v);
                if (profiles == null) {
                    profiles = new HashMap<>();
                }
                profiles.put(k, p);
            }
        }

        public ProjectDefinition build() {
            if (flows == null) {
                flows = new HashMap<>();
            }

            if (publicFlows == null) {
                publicFlows = new HashSet<>();
            }

            if (forms == null) {
                forms = new HashMap<>();
            }

            if (profiles == null) {
                profiles = new HashMap<>();
            }

            Map<String, Object> configuration = new LinkedHashMap<>();
            List<String> dependencies = new ArrayList<>();
            List<Trigger> triggers = new ArrayList<>();
            Imports imports = Imports.builder().build();

            // default resource paths
            Resources resources = Resources.DEFAULT;

            if (projectDefinitions != null) {
                for (ProjectDefinition pd : projectDefinitions) {
                    if (pd.getFlows() != null) {
                        flows.putAll(pd.getFlows());
                    }

                    if (pd.getPublicFlows() != null) {
                        publicFlows.addAll(pd.getPublicFlows());
                    }

                    if (pd.getForms() != null) {
                        forms.putAll(pd.getForms());
                    }

                    if (pd.getConfiguration() != null) {
                        Map<String, Object> cfg = pd.getConfiguration();

                        List<String> deps = MapUtils.getList(cfg, Constants.Request.DEPENDENCIES_KEY, Collections.emptyList());
                        dependencies.addAll(deps);

                        configuration = ConfigurationUtils.deepMerge(configuration, cfg);
                    }

                    if (pd.getProfiles() != null) {
                        for (Map.Entry<String, Profile> p : pd.getProfiles().entrySet()) {
                            merge(profiles, p.getKey(), p.getValue());
                        }
                    }

                    if (pd.getTriggers() != null) {
                        triggers.addAll(pd.getTriggers());
                    }

                    if (pd.getImports() != null) {
                        imports = Imports.builder()
                                .addAllItems(imports.items())
                                .addAllItems(pd.getImports().items())
                                .build();
                    }

                    if (pd.getResources() != null) {
                        // TODO avoid multiple conflicting resources definitions
                        resources = pd.getResources();
                    }
                }
            }

            configuration.put(Constants.Request.DEPENDENCIES_KEY, dependencies);

            return new ProjectDefinition(flows, publicFlows, forms, configuration, profiles, triggers, imports, resources);
        }

        private static boolean isYaml(Path p) {
            String n = p.getFileName().toString().toLowerCase();
            return n.endsWith(".yml") || n.endsWith(".yaml");
        }

        private static void merge(Map<String, Profile> profiles, String k, Profile b) {
            Profile a = profiles.get(k);
            if (a == null) {
                profiles.put(k, b);
                return;
            }

            Map<String, ProcessDefinition> flows = new HashMap<>();
            if (a.getFlows() != null) {
                flows.putAll(a.getFlows());
            }
            if (b.getFlows() != null) {
                flows.putAll(b.getFlows());
            }

            Set<String> publicFlows = new HashSet<>();
            if (a.getPublicFlows() != null) {
                publicFlows.addAll(a.getPublicFlows());
            }
            if (b.getPublicFlows() != null) {
                publicFlows.addAll(b.getPublicFlows());
            }

            Map<String, FormDefinition> forms = new HashMap<>();
            if (a.getForms() != null) {
                forms.putAll(a.getForms());
            }
            if (b.getForms() != null) {
                forms.putAll(b.getForms());
            }

            Map<String, Object> cfg = new HashMap<>();
            if (a.getConfiguration() != null) {
                cfg.putAll(a.getConfiguration());
            }
            if (b.getConfiguration() != null) {
                cfg = ConfigurationUtils.deepMerge(cfg, b.getConfiguration());
            }

            profiles.put(k, new Profile(flows, publicFlows, forms, cfg));
        }
    }

    public static class Result {

        private final List<Snapshot> snapshots;
        private final ProjectDefinition projectDefinition;

        public Result(List<Snapshot> snapshots, ProjectDefinition projectDefinition) {
            this.snapshots = snapshots;
            this.projectDefinition = projectDefinition;
        }

        public List<Snapshot> getSnapshots() {
            return snapshots;
        }

        public ProjectDefinition getProjectDefinition() {
            return projectDefinition;
        }
    }
}
