package com.walmartlabs.concord.cli;

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

import org.eclipse.jgit.ignore.IgnoreNode;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Utility class for filtering files based on .gitignore patterns.
 * Supports hierarchical .gitignore files in subdirectories per git spec.
 */
public class GitIgnoreFilter {

    private final Map<Path, IgnoreNode> ignoreNodes;

    private GitIgnoreFilter(Map<Path, IgnoreNode> ignoreNodes) {
        this.ignoreNodes = ignoreNodes;
    }

    /**
     * Loads all .gitignore files from the given directory and its subdirectories.
     *
     * @param baseDir the base directory to scan for .gitignore files
     * @return a GitIgnoreFilter instance, or null if no .gitignore files exist
     */
    public static GitIgnoreFilter load(Path baseDir) throws IOException {
        var nodes = new HashMap<Path, IgnoreNode>();

        Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                var gitignore = dir.resolve(".gitignore");
                if (Files.isRegularFile(gitignore)) {
                    var node = new IgnoreNode();
                    try (var in = Files.newInputStream(gitignore)) {
                        node.parse(in);
                    }
                    if (!node.getRules().isEmpty()) {
                        var relDir = baseDir.relativize(dir);
                        nodes.put(relDir, node);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (nodes.isEmpty()) {
            return null;
        }

        return new GitIgnoreFilter(nodes);
    }

    /**
     * Checks if the given path should be ignored based on .gitignore rules.
     *
     * @param relativePath the path relative to the base directory
     * @param isDirectory  true if the path is a directory
     * @return true if the path should be ignored
     */
    public boolean isIgnored(Path relativePath, boolean isDirectory) {
        var pathStr = relativePath.toString().replace('\\', '/');
        Boolean ignored = null;

        // Check from root down to parent directory
        for (var ancestor : getAncestorPaths(relativePath)) {
            var node = ignoreNodes.get(ancestor);
            if (node != null) {
                // Make path relative to this ignore node's directory
                String relativeToNode;
                if (ancestor.toString().isEmpty()) {
                    relativeToNode = pathStr;
                } else {
                    var ancestorStr = ancestor.toString().replace('\\', '/');
                    relativeToNode = pathStr.substring(ancestorStr.length() + 1);
                }

                var result = node.isIgnored(relativeToNode, isDirectory);
                if (result == IgnoreNode.MatchResult.IGNORED) {
                    ignored = true;
                } else if (result == IgnoreNode.MatchResult.NOT_IGNORED) {
                    ignored = false;
                }
                // CHECK_PARENT means no match, keep current value
            }
        }

        return Boolean.TRUE.equals(ignored);
    }

    /**
     * Returns all ancestor paths from root (empty path) to the parent of the given path.
     */
    private List<Path> getAncestorPaths(Path relativePath) {
        var ancestors = new ArrayList<Path>();
        ancestors.add(Paths.get(""));  // root

        var current = Paths.get("");
        for (var i = 0; i < relativePath.getNameCount() - 1; i++) {
            current = current.resolve(relativePath.getName(i));
            ancestors.add(current);
        }

        return ancestors;
    }
}
