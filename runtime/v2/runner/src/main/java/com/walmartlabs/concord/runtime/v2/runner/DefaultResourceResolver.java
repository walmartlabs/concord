package com.walmartlabs.concord.runtime.v2.runner;

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

import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;

import javax.inject.Inject;
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

public class DefaultResourceResolver implements ResourceResolver {

    private static final int MAX_CONTENT_LENGTH = 640 * 1024;

    private final WorkingDirectory workingDirectory;

    @Inject
    public DefaultResourceResolver(WorkingDirectory workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    public InputStream resolve(String name) throws IOException {
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
            return ClassLoader.getSystemResourceAsStream(name);
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
        Path p = workingDirectory.getValue().resolve(name);
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
