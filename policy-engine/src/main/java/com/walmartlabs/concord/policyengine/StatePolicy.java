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
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class StatePolicy {

    public static class StateStats {

        private final long size;
        private final int filesCount;

        public StateStats(long size, int filesCount) {
            this.size = size;
            this.filesCount = filesCount;
        }

        public long getSize() {
            return size;
        }

        public int getFilesCount() {
            return filesCount;
        }
    }

    private final PolicyRules<StateRule> rules;

    public StatePolicy(PolicyRules<StateRule> rules) {
        this.rules = rules;
    }

    public CheckResult<StateRule, StateStats> check(Supplier<StateStats> statsSupplier) {
        if (rules == null || rules.isEmpty()) {
            return CheckResult.success();
        }

        List<StateRule> warnRules = rules.getWarn().stream().filter(StatePolicy::hasStats).collect(Collectors.toList());
        List<StateRule> denyRules = rules.getDeny().stream().filter(StatePolicy::hasStats).collect(Collectors.toList());
        if (warnRules.isEmpty() && denyRules.isEmpty()) {
            return CheckResult.success();
        }

        List<CheckResult.Item<StateRule, StateStats>> warn = new ArrayList<>();
        List<CheckResult.Item<StateRule, StateStats>> deny = new ArrayList<>();

        StateStats stats = statsSupplier.get();

        checkStats(warnRules, stats, warn);
        checkStats(denyRules, stats, deny);

        return new CheckResult<>(warn, deny);
    }

    public CheckResult<StateRule, Path> check(Path src, BiFunction<Path, BasicFileAttributes, Boolean> filter) throws IOException {
        if (rules == null || rules.isEmpty()) {
            return CheckResult.success();
        }

        if (Files.notExists(src)) {
            return CheckResult.success();
        }

        List<StateRule> warnRules = rules.getWarn().stream().filter(r -> !r.patterns().isEmpty()).collect(Collectors.toList());
        List<StateRule> denyRules = rules.getDeny().stream().filter(r -> !r.patterns().isEmpty()).collect(Collectors.toList());
        if (warnRules.isEmpty() && denyRules.isEmpty()) {
            return CheckResult.success();
        }

        List<CheckResult.Item<StateRule, Path>> warn = new ArrayList<>();
        List<CheckResult.Item<StateRule, Path>> deny = new ArrayList<>();

        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!filter.apply(file, attrs)) {
                    return FileVisitResult.CONTINUE;
                }

                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    return FileVisitResult.CONTINUE;
                }

                checkPatterns(warnRules, file, warn);
                checkPatterns(denyRules, file, deny);

                return FileVisitResult.CONTINUE;
            }
        });

        return new CheckResult<>(warn, deny);
    }

    private static void checkStats(List<StateRule> rules, StateStats stats, List<CheckResult.Item<StateRule, StateStats>> result) {
        for (StateRule r : rules) {
            if (r.maxFilesCount() != null && stats.getFilesCount() > r.maxFilesCount()) {
                result.add(new CheckResult.Item<>(r, stats, "Max files count exceeded. Actual: " + stats.getFilesCount() + " limit: " + r.maxFilesCount()));
            }

            if (r.maxSizeInBytes() != null && stats.getSize() > r.maxSizeInBytes()) {
                result.add(new CheckResult.Item<>(r, stats, "Max state size exceeded. Actual: " + stats.getSize() + " limit: " + r.maxSizeInBytes()));
            }
        }
    }

    private static void checkPatterns(List<StateRule> rules, Path file, List<CheckResult.Item<StateRule, Path>> result) {
        for (StateRule r : rules) {
            if (matchPattern(r.patterns(), file.toString())) {
                result.add(new CheckResult.Item<>(r, file));
            }
        }
    }

    private static boolean hasStats(StateRule rule) {
        return rule.maxFilesCount() != null || rule.maxSizeInBytes() != null;
    }

    private static boolean matchPattern(List<String> patterns, String fileName) {
        return Utils.matchAny(patterns, fileName);
    }
}
