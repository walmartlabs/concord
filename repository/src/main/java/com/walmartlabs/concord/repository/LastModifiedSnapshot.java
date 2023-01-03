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

import com.walmartlabs.concord.common.FileVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;

public class LastModifiedSnapshot implements Snapshot, FileVisitor {

    private final Map<Path, FileTime> files = new HashMap<>();

    @Override
    public void visit(Path src, Path dst) throws IOException {
        files.put(dst.normalize(), Files.getLastModifiedTime(dst));
    }

    @Override
    public boolean contains(Path path) {
        return files.get(path.normalize()) != null;
    }

    @Override
    public boolean isModified(Path path, BasicFileAttributes attrs) {
        FileTime prev = files.get(path.normalize());
        if (prev == null) {
            return true;
        }

        return !prev.equals(attrs.lastModifiedTime());
    }
}
