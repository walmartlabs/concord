package com.walmartlabs.concord.cli.runner.secrets;

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

import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

public final class UncheckedIO {

    public static Path assertTmpDir(Path workDir) {
        var dir = workDir.resolve("target").resolve(Constants.Files.CONCORD_TMP_DIR_NAME);
        if (Files.notExists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
        return dir;
    }

    public static Path write(Path path, byte[] bytes, OpenOption... options) {
        try {
            return Files.write(path, bytes, options);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static byte[] readAllBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static Path createTempFile(Path dir, String prefix, String suffix) {
        try {
            return Files.createTempFile(dir, prefix, suffix);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static Path copy(Path source, Path target, CopyOption... options) {
        try {
            return Files.copy(source, target, options);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private UncheckedIO() {
    }
}
