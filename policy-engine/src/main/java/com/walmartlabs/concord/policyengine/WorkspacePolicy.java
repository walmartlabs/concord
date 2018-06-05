package com.walmartlabs.concord.policyengine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import org.apache.commons.lang3.mutable.MutableLong;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorkspacePolicy {

    private final WorkspaceRule rule;

    public WorkspacePolicy(WorkspaceRule rule) {
        this.rule = rule;
    }

    public CheckResult<WorkspaceRule, Path> check(Path p) throws IOException {
        if (rule == null) {
            return new CheckResult<>();
        }

        List<CheckResult.Item<WorkspaceRule, Path>> deny = new ArrayList<>();

        if (!Files.exists(p) || !Files.isDirectory(p)) {
            // TODO pass additional error information
            deny.add(new CheckResult.Item<>(rule, p));
        }

        if (rule.getMaxSizeInBytes() != null) {
            MutableLong size = new MutableLong();
            Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (Files.isRegularFile(file)) {
                        size.add(Files.size(file));
                    }

                    return FileVisitResult.CONTINUE;
                }
            });

            if (size.toLong() > rule.getMaxSizeInBytes()) {
                deny.add(new CheckResult.Item<>(rule, p));
            }
        }

        return new CheckResult<>(Collections.emptyList(), deny);
    }
}
