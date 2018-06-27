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


import com.walmartlabs.concord.project.model.Profile;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.model.Trigger;
import com.walmartlabs.concord.project.yaml.*;
import com.walmartlabs.concord.project.yaml.model.*;
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

public class ProjectLoader {

    public static final String PROJECT_FILE_NAME = ".concord.yml";

    public static final String[] PROJECT_FILE_NAMES = {".concord.yml", "concord.yml"};

    private final YamlParser parser = new YamlParser();

    public ProjectDefinition load(Path baseDir) throws IOException {
        ProjectDefinitionBuilder b = new ProjectDefinitionBuilder(parser);

        for (String n : PROJECT_FILE_NAMES) {
            Path projectFile = baseDir.resolve(n);
            if (Files.exists(projectFile)) {
                b.addProjectFile(projectFile);
                break;
            }
        }

        for (String n : InternalConstants.Files.DEFINITIONS_DIR_NAMES) {
            Path defsDir = baseDir.resolve(n);
            if (Files.exists(defsDir)) {
                b.addDefinitions(defsDir);
            }
        }

        Path profilesDir = baseDir.resolve(InternalConstants.Files.PROFILES_DIR_NAME);
        if (Files.exists(profilesDir)) {
            b.addProfiles(profilesDir);
        }

        return b.build();
    }

    public ProjectDefinition load(InputStream in) throws IOException {
        ProjectDefinitionBuilder b = new ProjectDefinitionBuilder(parser);
        b.loadDefinitions(in);
        return b.build();
    }

    private static class ProjectDefinitionBuilder {

        private final YamlParser parser;

        private Map<String, ProcessDefinition> flows;
        private Map<String, FormDefinition> forms;
        private Map<String, Profile> profiles;
        private ProjectDefinition projectDefinition;

        private ProjectDefinitionBuilder(YamlParser parser) {
            this.parser = parser;
        }

        public ProjectDefinitionBuilder addProjectFile(Path path) throws IOException {
            YamlProject yml = parser.parseProject(path);
            if (yml == null) {
                throw new IOException("Project definition not found: " + path);
            }

            this.projectDefinition = YamlProjectConverter.convert(yml);
            return this;
        }

        public void addDefinitions(Path path) throws IOException {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    loadDefinitions(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        public void addProfiles(Path path) throws IOException {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    loadProfiles(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        private void loadDefinitions(Path file) throws IOException {
            String n = file.getFileName().toString();
            if (!n.endsWith(".yml") && !n.endsWith(".yaml")) {
                return;
            }

            YamlDefinitionFile df = parser.parseDefinitionFile(file);
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

        private void loadProfiles(Path file) throws IOException {
            String n = file.getFileName().toString();
            if (!n.endsWith(".yml") && !n.endsWith(".yaml")) {
                return;
            }

            YamlProfileFile pf = parser.parseProfileFile(file);
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

            if (forms == null) {
                forms = new HashMap<>();
            }

            if (profiles == null) {
                profiles = new HashMap<>();
            }

            Map<String, Object> variables = new LinkedHashMap<>();
            List<Trigger> triggers =  new ArrayList<>();

            if (projectDefinition != null) {
                if (projectDefinition.getFlows() != null) {
                    flows.putAll(projectDefinition.getFlows());
                }

                if (projectDefinition.getForms() != null) {
                    forms.putAll(projectDefinition.getForms());
                }

                if (projectDefinition.getConfiguration() != null) {
                    variables.putAll(projectDefinition.getConfiguration());
                }

                if (projectDefinition.getProfiles() != null) {
                    profiles.putAll(projectDefinition.getProfiles());
                }

                if (projectDefinition.getTriggers() != null) {
                    triggers.addAll(projectDefinition.getTriggers());
                }
            }

            return new ProjectDefinition(flows, forms, variables, profiles, triggers);
        }
    }
}
