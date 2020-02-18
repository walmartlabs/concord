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

import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.parser.YamlParserV2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

// TODO rename to ProcessDefinitionLoader?
public class ProjectLoaderV2 {

    public static final String[] PROJECT_FILE_NAMES = {".concord.yml", "concord.yml"};

    public Result load(Path baseDir) throws IOException {
        YamlParserV2 parser = new YamlParserV2();

        // TODO update to the latest V1 signature, add ImportManager, etc
        // TODO load resource definitions

        // default dir structure
        // ./concord.yml
        Path p = baseDir.resolve("concord.yml");
        if (Files.exists(p)) {
            return new Result(Collections.emptyList(), parser.parse(baseDir, p));
        }

        // ./concord/**/*.concord.yml
        // ./concord/forms/**/index.html

        throw new IllegalStateException("Can't find any Concord process definition files: " + baseDir);
    }

    public Result loadFromFile(Path path) throws IOException {
        YamlParserV2 parser = new YamlParserV2();

        if (Files.notExists(path)) {
            throw new IllegalStateException("Can't find Concord process definition file: " + path);
        }

        return new Result(Collections.emptyList(), parser.parse(path.getParent(), path));
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
