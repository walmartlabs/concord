package com.walmartlabs.concord.runner.engine;

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

import io.takari.bpm.resource.ClassPathResourceResolver;
import io.takari.bpm.resource.ResourceResolver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResourceResolverImpl implements ResourceResolver {

    private static final int MAX_CONTENT_LENGTH = 640 * 1024; // "640kB ought to be enough for anybody"

    private final ResourceResolver classPathDelegate = new ClassPathResourceResolver();
    private final Path baseDir;

    public ResourceResolverImpl(Path baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public InputStream getResourceAsStream(String name) throws IOException {
        try {
            URL url = new URL(name);
            return toStream(url);
        } catch (MalformedURLException e) {
            // not a URL, let's try a file first...
            InputStream in = toStream(name);
            if (in != null) {
                return in;
            }

            //...or look in the classpath
            return classPathDelegate.getResourceAsStream(name);
        }
    }

    private InputStream toStream(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        int length = -1;

        if (conn instanceof HttpURLConnection) {
            HttpURLConnection httpConn = (HttpURLConnection) conn;

            String s = httpConn.getHeaderField("Content-Length");
            if (s != null) {
                length = Integer.parseInt(s);
            }
        }

        assertContentLength(length);

        ByteArrayOutputStream out;
        if (length > 0) {
            out = new ByteArrayOutputStream(length);
        } else {
            out = new ByteArrayOutputStream();
        }

        byte[] buf = new byte[4096];
        int read;

        try (InputStream in = conn.getInputStream()) {
            while ((read = in.read(buf)) > 0) {
                assertContentLength(out.size());
                out.write(buf, 0, read);
            }
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    private InputStream toStream(String name) throws IOException {
        Path p = baseDir.resolve(name);
        if (!Files.exists(p)) {
            return null;
        }
        return Files.newInputStream(p);
    }

    private static void assertContentLength(int current) throws IOException {
        if (current > MAX_CONTENT_LENGTH) {
            throw new IOException("Content too long: " + current + " (max: " + MAX_CONTENT_LENGTH + ")");
        }
    }
}
