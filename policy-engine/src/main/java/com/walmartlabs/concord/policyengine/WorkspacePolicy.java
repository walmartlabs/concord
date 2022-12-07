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
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class WorkspacePolicy {

    private final WorkspaceRule rule;

    public WorkspacePolicy(WorkspaceRule rule) {
        this.rule = rule;
    }

    public CheckResult<WorkspaceRule, Path> check(Path p) throws IOException {
        if (rule == null) {
            return CheckResult.success();
        }

        List<CheckResult.Item<WorkspaceRule, Path>> deny = new ArrayList<>();

        if (!Files.exists(p)) {
            deny.add(new CheckResult.Item<>(rule, p, "File not found: " + p));
        } else if (!Files.isDirectory(p)) {
            deny.add(new CheckResult.Item<>(rule, p, "Not a directory: " + p));
        } else if (rule.maxSizeInBytes() != null) {
            Long[] size = { 0L };

            Files.walkFileTree(p, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (isIgnored(file, rule.ignoredFiles())) {
                        return FileVisitResult.CONTINUE;
                    }

                    size[0] += Files.size(file);

                    return FileVisitResult.CONTINUE;
                }
            });

            if (size[0] > rule.maxSizeInBytes()) {
                deny.add(new CheckResult.Item<>(rule, p, "Workspace too big: " + size[0] + " byte(s)"));
            }
        }

        return new CheckResult<>(Collections.emptyList(), deny);
    }

    private static boolean isIgnored(Path p, Set<String> patterns) {
        if (patterns == null) {
            return false;
        }

        for (String s : patterns) {
            if (p.toString().matches(s)) {
                return true;
            }
        }

        return false;
    }
}
