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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GitIgnoreFilterTest {

    @TempDir
    Path tempDir;

    @Test
    void testNoGitignoreReturnsNull() throws IOException {
        var filter = GitIgnoreFilter.load(tempDir);
        assertNull(filter);
    }

    @Test
    void testEmptyGitignoreReturnsNull() throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), "");
        var filter = GitIgnoreFilter.load(tempDir);
        assertNull(filter);
    }

    @Test
    void testCommentsOnlyReturnsNull() throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), "# This is a comment\n# Another comment\n");
        var filter = GitIgnoreFilter.load(tempDir);
        assertNull(filter);
    }

    @Test
    void testBasicPatterns() throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), "*.log\nnode_modules/\n");

        var filter = GitIgnoreFilter.load(tempDir);
        assertNotNull(filter);

        assertTrue(filter.isIgnored(Path.of("debug.log"), false));
        assertTrue(filter.isIgnored(Path.of("error.log"), false));
        assertFalse(filter.isIgnored(Path.of("debug.txt"), false));

        assertTrue(filter.isIgnored(Path.of("node_modules"), true));
        assertFalse(filter.isIgnored(Path.of("other_modules"), true));
    }

    @Test
    void testGlobPatterns() throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), "**/*.tmp\nbuild/**/output\n");

        var filter = GitIgnoreFilter.load(tempDir);
        assertNotNull(filter);

        assertTrue(filter.isIgnored(Path.of("test.tmp"), false));
        assertTrue(filter.isIgnored(Path.of("sub/dir/test.tmp"), false));
        assertFalse(filter.isIgnored(Path.of("test.txt"), false));

        assertTrue(filter.isIgnored(Path.of("build/output"), true));
        assertTrue(filter.isIgnored(Path.of("build/sub/output"), true));
    }

    @Test
    void testNegationPatterns() throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), "*.log\n!important.log\n");

        var filter = GitIgnoreFilter.load(tempDir);
        assertNotNull(filter);

        assertTrue(filter.isIgnored(Path.of("debug.log"), false));
        assertTrue(filter.isIgnored(Path.of("error.log"), false));
        assertFalse(filter.isIgnored(Path.of("important.log"), false));
    }

    @Test
    void testDirectoryOnlyPatterns() throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), "build/\n");

        var filter = GitIgnoreFilter.load(tempDir);
        assertNotNull(filter);

        assertTrue(filter.isIgnored(Path.of("build"), true));
        // A file named "build" should not be ignored when pattern has trailing slash
        assertFalse(filter.isIgnored(Path.of("build"), false));
    }

    @Test
    void testAnchoredPatterns() throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), "/config.local\n");

        var filter = GitIgnoreFilter.load(tempDir);
        assertNotNull(filter);

        assertTrue(filter.isIgnored(Path.of("config.local"), false));
        // Anchored pattern should not match in subdirectories
        assertFalse(filter.isIgnored(Path.of("sub/config.local"), false));
    }

    @Test
    void testNestedGitignore() throws IOException {
        // Root .gitignore
        Files.writeString(tempDir.resolve(".gitignore"), "*.log\n");

        // Create subdirectory with its own .gitignore
        var subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve(".gitignore"), "*.txt\n!keep.txt\n");

        var filter = GitIgnoreFilter.load(tempDir);
        assertNotNull(filter);

        // Root patterns apply everywhere
        assertTrue(filter.isIgnored(Path.of("debug.log"), false));
        assertTrue(filter.isIgnored(Path.of("subdir/debug.log"), false));

        // Subdir patterns only apply within subdir
        assertFalse(filter.isIgnored(Path.of("test.txt"), false));
        assertTrue(filter.isIgnored(Path.of("subdir/test.txt"), false));
        assertFalse(filter.isIgnored(Path.of("subdir/keep.txt"), false));
    }

    @Test
    void testBlankLinesIgnored() throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), "*.log\n\n\n*.tmp\n");

        var filter = GitIgnoreFilter.load(tempDir);
        assertNotNull(filter);

        assertTrue(filter.isIgnored(Path.of("debug.log"), false));
        assertTrue(filter.isIgnored(Path.of("test.tmp"), false));
    }

    @Test
    void testMixedPatterns() throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), """
                # Dependencies
                node_modules/

                # Build output
                build/
                dist/
                *.class

                # IDE
                .idea/
                *.iml

                # Logs
                *.log
                !important.log

                # Temp files
                **/*.tmp
                """);

        var filter = GitIgnoreFilter.load(tempDir);
        assertNotNull(filter);

        assertTrue(filter.isIgnored(Path.of("node_modules"), true));
        assertTrue(filter.isIgnored(Path.of("build"), true));
        assertTrue(filter.isIgnored(Path.of("dist"), true));
        assertTrue(filter.isIgnored(Path.of("Main.class"), false));
        assertTrue(filter.isIgnored(Path.of(".idea"), true));
        assertTrue(filter.isIgnored(Path.of("project.iml"), false));
        assertTrue(filter.isIgnored(Path.of("app.log"), false));
        assertFalse(filter.isIgnored(Path.of("important.log"), false));
        assertTrue(filter.isIgnored(Path.of("deep/nested/file.tmp"), false));
    }

    @Test
    void testSubdirectoryPaths() throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), "target/\n");

        var filter = GitIgnoreFilter.load(tempDir);
        assertNotNull(filter);

        assertTrue(filter.isIgnored(Path.of("target"), true));
        assertTrue(filter.isIgnored(Path.of("sub/target"), true));
    }

    @Test
    void testFilesInIgnoredDirectory() throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), "build/\n");

        var filter = GitIgnoreFilter.load(tempDir);
        assertNotNull(filter);

        // The directory itself is ignored
        assertTrue(filter.isIgnored(Path.of("build"), true));
        // Files inside an ignored directory are NOT directly matched by the pattern.
        // In practice, the walk would skip the directory entirely, so this case wouldn't occur.
        // The pattern `build/` only matches directories, not paths within them.
        assertFalse(filter.isIgnored(Path.of("build/output.jar"), false));
    }
}
