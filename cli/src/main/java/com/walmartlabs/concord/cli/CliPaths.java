package com.walmartlabs.concord.cli;

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

import java.nio.file.Path;

public final class CliPaths {

    public static final String DEFAULT_TARGET_DIR_NAME = "target";

    public static Path defaultTargetDir(Path sourceDir) {
        return sourceDir.resolve(DEFAULT_TARGET_DIR_NAME);
    }

    public static Path preferredResumeDir(Path sourceDir, Path workDir) {
        var normalizedSourceDir = sourceDir.normalize().toAbsolutePath();
        var normalizedWorkDir = workDir.normalize().toAbsolutePath();
        if (normalizedWorkDir.equals(defaultTargetDir(normalizedSourceDir))) {
            return normalizedSourceDir;
        }
        return normalizedWorkDir;
    }

    private CliPaths() {
    }
}
