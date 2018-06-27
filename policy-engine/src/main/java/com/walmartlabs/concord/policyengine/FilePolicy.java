package com.walmartlabs.concord.policyengine;

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


import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static  com.walmartlabs.concord.policyengine.Utils.matchAny;

public class FilePolicy {

    private final PolicyRules<FileRule> rules;

    public FilePolicy(PolicyRules<FileRule> rules) {
        this.rules = rules;
    }

    public CheckResult<FileRule, Path> check(Path p) throws IOException {
        if (rules == null || rules.isEmpty()) {
            return new CheckResult<>();
        }

        List<CheckResult.Item<FileRule, Path>> warn = new ArrayList<>();
        List<CheckResult.Item<FileRule, Path>> deny = new ArrayList<>();

        Files.walkFileTree(p, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                for(FileRule r : rules.getAllow()) {
                    if (matchRule(dir, r, FileRule.Type.DIR)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                for(FileRule r : rules.getAllow()) {
                    if (matchRule(file, r, FileRule.Type.FILE)) {
                        return FileVisitResult.CONTINUE;
                    }
                }

                for(FileRule r : rules.getDeny()) {
                    if (matchRule(file, r, FileRule.Type.FILE)) {
                        deny.add(new CheckResult.Item<>(r, file));
                        return FileVisitResult.CONTINUE;
                    }
                }

                for(FileRule r : rules.getWarn()) {
                    if (matchRule(file, r, FileRule.Type.FILE)) {
                        warn.add(new CheckResult.Item<>(r, file));
                        return FileVisitResult.CONTINUE;
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        });

        return new CheckResult<>(warn, deny);
    }

    private boolean matchRule(Path file, FileRule ri, FileRule.Type type) throws IOException {
        if (ri.getType() != type) {
            return false;
        }

        if (!ri.getNames().isEmpty() && !matchAny(ri.getNames(), file.getFileName().toString())) {
            return false;
        }

        if (ri.getMaxSizeInBytes() != null && Files.size(file) < ri.getMaxSizeInBytes()) {
            return false;
        }

        return true;
    }
}
