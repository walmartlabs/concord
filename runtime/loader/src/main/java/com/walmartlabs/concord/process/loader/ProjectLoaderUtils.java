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

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static com.walmartlabs.concord.process.loader.StandardRuntimeTypes.PROJECT_ROOT_FILE_NAMES;

public class ProjectLoaderUtils {

    public static Optional<String> getRuntimeType(Path workDir) throws IOException {
        for (var filename : PROJECT_ROOT_FILE_NAMES) {
            var src = workDir.resolve(filename);
            if (Files.exists(src)) {
                var mapper = new YAMLMapper();
                try (var in = Files.newInputStream(src)) {
                    var n = mapper.readTree(in);

                    n = n.get(Constants.Request.CONFIGURATION_KEY);
                    if (n == null) {
                        continue;
                    }

                    n = n.get(Constants.Request.RUNTIME_KEY);
                    if (n == null) {
                        continue;
                    }

                    var s = n.textValue();
                    if (s != null) {
                        return Optional.of(s);
                    }
                }
            }
        }

        return Optional.empty();
    }

    public static boolean isConcordFileExists(Path repoPath) {
        for (String projectFileName : PROJECT_ROOT_FILE_NAMES) {
            Path projectFile = repoPath.resolve(projectFileName);
            if (Files.exists(projectFile)) {
                return true;
            }
        }

        return false;
    }

    private ProjectLoaderUtils() {
    }
}
