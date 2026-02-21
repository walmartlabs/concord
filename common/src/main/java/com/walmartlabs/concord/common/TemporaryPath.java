package com.walmartlabs.concord.common;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TemporaryPath implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TemporaryPath.class);

    private final Path path;

    public TemporaryPath(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() {
        if (path == null) {
            return;
        }

        try {
            if (Files.isDirectory(path)) {
                PathUtils.deleteRecursively(path);
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("cleanup ['{}'] -> error: {}", path, e.getMessage());
        }
    }
}
