package com.walmartlabs.concord.common;

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
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ResourceUtils {

    public static void copyResources(Path baseDir, List<String> includePatterns, Path destDir, CopyOption... options) throws IOException {
        copyResources(baseDir, includePatterns, List.of(), destDir, null, options);
    }

    /**
     * Copies Concord resources from baseDir to destDir.
     * @param baseDir source
     * @param includePatterns list of paths (glob, regexes, file paths) to include
     * @param excludePatterns list of paths (glob, regexes, file paths) to exclude
     * @param destDir destination
     * @param visitor a FileVisitor to apply after each copied file (can be null)
     * @param options array of CopyOptions
     * @throws IOException
     */
    public static void copyResources(Path baseDir, List<String> includePatterns, List<String> excludePatterns, Path destDir, FileVisitor visitor, CopyOption... options) throws IOException {
        var paths = findResources(baseDir, includePatterns, excludePatterns);
        for (var fileName : Constants.Files.PROJECT_ROOT_FILE_NAMES) {
            var p = baseDir.resolve(fileName);
            paths.add(p);
        }
        copy(paths, baseDir, destDir, visitor, options);
    }

    public static List<Path> findResources(Path baseDir, List<String> includePatterns) throws IOException {
        return findResources(baseDir, includePatterns, List.of());
    }

    /**
     * Finds paths in baseDir that match includePatterns and do not match excludePatterns.
     * @param baseDir source
     * @param includePatterns list of paths (glob, regexes, file paths) to include
     * @param excludePatterns list of paths (glob, regexes, file paths) to exclude
     * @return list of absolute paths
     * @throws IOException
     */
    public static List<Path> findResources(Path baseDir, List<String> includePatterns, List<String> excludePatterns) throws IOException {
        var result = new ArrayList<Path>();

        var includeMatchers = includePatterns.stream().map(p -> parsePattern(baseDir, p)).toList();
        var excludeMatchers = excludePatterns.stream().map(p -> parsePattern(baseDir, p)).toList();

        try (var walker = Files.walk(baseDir)) {
            walker.filter(candidate -> !Files.isDirectory(candidate))
                    .filter(candidate ->
                            includeMatchers.stream().anyMatch(m -> m.matches(candidate)) &&
                            excludeMatchers.stream().noneMatch(pattern -> pattern.matches(candidate)))
                    .forEach(result::add);
        }

        return result;
    }

    private static void copy(Collection<Path> files, Path baseDir, Path dest, FileVisitor visitor, CopyOption... options) throws IOException {
        for (var f : files) {
            if (Files.notExists(f)) {
                continue;
            }

            var src = baseDir.relativize(f);
            var dst = dest.resolve(src);

            var dstParent = dst.getParent();
            if (dstParent != null && Files.notExists(dstParent)) {
                Files.createDirectories(dstParent);
            }

            if (Files.isSymbolicLink(f)) {
                Path link = Files.readSymbolicLink(f);
                Path target = f.getParent().resolve(link).normalize();

                if (!target.startsWith(baseDir)) {
                    throw new IOException("Symlinks outside the base directory are not supported: " + baseDir + " -> " + target);
                }

                if (Files.notExists(target)) {
                    continue;
                }

                Files.createSymbolicLink(dst, link);
            } else {
                Files.copy(f, dst, options);
            }

            if (visitor != null) {
                visitor.visit(src, dst);
            }
        }
    }

    static PathMatcher parsePattern(Path baseDir, String pattern) {
        pattern = pattern.trim();

        String normalizedPattern;
        if (pattern.startsWith("glob:")) {
            normalizedPattern = "glob:" + concat(baseDir, pattern.substring("glob:".length()));
        } else if (pattern.startsWith("regex:")) {
            normalizedPattern = "regex:" + concat(baseDir, pattern.substring("regex:".length()));
        } else {
            normalizedPattern = "glob:" + concat(baseDir, pattern
                    .replace("*", "\\*")
                    .replace("{", "\\{")
                    .replace("}", "\\}")
                    .replace("[", "\\[")
                    .replace("]", "\\]")
                    .replace("!", "\\!")
                    .replace("?", "\\?"));
        }

        return FileSystems.getDefault().getPathMatcher(normalizedPattern);
    }

    private static String concat(Path path, String str) {
        var separator = "/";
        if (str.startsWith("/")) {
            separator = "";
        }
        return path.toAbsolutePath() + separator + str;
    }

    private ResourceUtils() {
    }
}
