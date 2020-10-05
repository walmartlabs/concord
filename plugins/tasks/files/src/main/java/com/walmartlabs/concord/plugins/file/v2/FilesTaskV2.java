package com.walmartlabs.concord.plugins.file.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Files;
import java.nio.file.Path;

@Named("files")
@SuppressWarnings("unused")
public class FilesTaskV2 implements Task {

    private final Path workDir;

    @Inject
    public FilesTaskV2(Context context) {
        this.workDir = context.workingDirectory();
    }

    public boolean exists(String path) {
        return Files.exists(assertPath(workDir, path));
    }

    public boolean notExists(String path) {
        return Files.notExists(assertPath(workDir, path));
    }

    private static Path assertPath(Path workDir, String path) {
        Path result = workDir.resolve(path).normalize().toAbsolutePath();
        if (!result.startsWith(workDir)) {
            throw new IllegalArgumentException("The path must be within the working directory: " + path);
        }
        return result;
    }
}
