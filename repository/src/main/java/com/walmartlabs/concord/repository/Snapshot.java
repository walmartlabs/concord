package com.walmartlabs.concord.repository;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Reflects the state of the process working directory while it is processed
 * by the Server. Allows the Server to know which files are updated during the
 * working directory preparation and which can be recreated using external
 * sources (i.e. SCM repositories).
 */
public interface Snapshot {

    /**
     * @param file absolute path
     */
    static Snapshot singleFile(Path file) {
        return new Snapshot() {
            @Override
            public boolean isModified(Path path, BasicFileAttributes attrs) {
                return true;
            }

            @Override
            public boolean contains(Path path) {
                return file.equals(path);
            }
        };
    }

    static Snapshot includeAll() {
        return new Snapshot() {

            @Override
            public boolean isModified(Path path, BasicFileAttributes attrs) {
                return true;
            }

            @Override
            public boolean contains(Path path) {
                return true;
            }
        };
    }

    boolean isModified(Path path, BasicFileAttributes attrs);

    boolean contains(Path path);
}
