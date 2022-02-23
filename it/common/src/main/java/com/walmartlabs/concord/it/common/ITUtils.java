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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.Constants;
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
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class ITUtils {

    private static final char[] RANDOM_CHARS = "abcdef0123456789".toCharArray();

    public static byte[] archive(URI uri) throws IOException {
        return archive(uri, null);
    }

    public static byte[] archive(URI uri, String depsDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
            IOUtils.zip(zip, Paths.get(uri));
            if (depsDir != null) {
                IOUtils.zip(zip, Constants.Files.LIBRARIES_DIR_NAME + "/", Paths.get(depsDir));
            }
        }
        return out.toByteArray();
    }

    public static URI resourceToURI(Class<?> klass, String resource) throws URISyntaxException {
        URL url = klass.getResource(resource);
        if (url == null) {
            throw new RuntimeException("Resource not found: " + resource);
        }

        return url.toURI();
    }

    public static String resourceToString(Class<?> klass, String resource) throws Exception {
        URL url = klass.getResource(resource);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = url.openStream()) {
            IOUtils.copy(in, out);
        }

        return new String(out.toByteArray());
    }

    public static Path createTempDir() throws IOException {
        Path dir = IOUtils.createTempDir("test");
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        return dir;
    }

    public static String createGitRepo(Class<?> klass, String resource) throws IOException, GitAPIException, URISyntaxException {
        Path src = Paths.get(klass.getResource(resource).toURI());
        return createGitRepo(src);
    }

    public static String createGitRepo(Path src) throws IOException, GitAPIException {
        Path tmpDir = createTempDir();
        IOUtils.copy(src, tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

        return tmpDir.toAbsolutePath().toString();
    }

    public static String randomString() {
        return System.currentTimeMillis() + "_" + randomString(6);
    }

    public static String randomString(int length) {
        StringBuilder b = new StringBuilder();

        Random rng = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            int n = rng.nextInt(RANDOM_CHARS.length);
            b.append(RANDOM_CHARS[n]);
        }

        return b.toString();
    }

    public static String randomPwd() {
        return "pwd_" + randomString() + "A!";
    }

    private ITUtils() {
    }
}
