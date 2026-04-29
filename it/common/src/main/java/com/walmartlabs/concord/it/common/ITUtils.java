package com.walmartlabs.concord.it.common;

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

import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.common.ZipUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public final class ITUtils {

    private static final char[] RANDOM_CHARS = "abcdef0123456789".toCharArray();

    public static byte[] archive(URI uri) throws IOException {
        try (TemporaryPath tmpDir = preprocessDir(uri)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
                ZipUtils.zip(zip, tmpDir.path());
            }
            return out.toByteArray();
        }
    }

    public static String resourceToString(Class<?> klass, String resource) throws Exception {
        URL url = klass.getResource(resource);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = url.openStream()) {
            in.transferTo(out);
        }

        return new String(out.toByteArray());
    }

    public static Path createTempDir() throws IOException {
        Path dir = PathUtils.createTempDir("test");
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        return dir;
    }

    public static String createGitRepo(Class<?> klass, String resource) throws IOException, GitAPIException, URISyntaxException {
        Path src = Paths.get(klass.getResource(resource).toURI());
        return createGitRepo(src);
    }

    public static String createGitRepo(Path src) throws IOException, GitAPIException {
        Path tmpDir = createTempDir();
        PathUtils.copy(src, tmpDir);

        Git repo = Git.init().setInitialBranch("master").setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

        return tmpDir.toAbsolutePath().toString();
    }

    public static String randomString() {
        StringBuilder b = new StringBuilder();
        b.append(System.currentTimeMillis()).append("_");

        Random rng = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            int n = rng.nextInt(RANDOM_CHARS.length);
            b.append(RANDOM_CHARS[n]);
        }

        return b.toString();
    }

    public static String randomPwd() {
        return "pwd_" + randomString() + "A!";
    }

    private static TemporaryPath preprocessDir(URI uri) throws IOException {
        Path src = Paths.get(uri);

        // copy files from the specified URI to a temporary directory
        TemporaryPath tmpDir = PathUtils.tempDir("test");
        PathUtils.copy(src, tmpDir.path());

        // find and replace all PROJECT_VERSION strings with the current ${project.version}
        try (Stream<Path> yamlFiles = Files.walk(tmpDir.path())) {
            for (Path yamlFile : yamlFiles.filter(f -> {
                String fileName = f.getFileName().toString().toLowerCase();
                return fileName.endsWith(".yaml") || fileName.endsWith(".yml") || fileName.endsWith(".json");
            }).toList()) {
                String content = Files.readString(yamlFile);
                if (!content.contains("PROJECT_VERSION")) {
                    continue;
                }
                content = content.replaceAll("PROJECT_VERSION", Version.PROJECT_VERSION);
                Files.writeString(yamlFile, content, StandardOpenOption.TRUNCATE_EXISTING);
            }
        }

        return tmpDir;
    }

    private ITUtils() {
    }
}
