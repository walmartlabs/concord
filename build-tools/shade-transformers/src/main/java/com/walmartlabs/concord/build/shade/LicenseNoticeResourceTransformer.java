package com.walmartlabs.concord.build.shade;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ReproducibleResourceTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class LicenseNoticeResourceTransformer implements ReproducibleResourceTransformer {

    private static final String LICENSE_PATH = "META-INF/LICENSE";

    private static final String NOTICE_PATH = "META-INF/NOTICE";

    private final List<byte[]> licenses = new ArrayList<>();

    private final List<byte[]> notices = new ArrayList<>();

    private long licenseTime = Long.MIN_VALUE;

    private long noticeTime = Long.MIN_VALUE;

    @Override
    public boolean canTransformResource(String resource) {
        return isLicense(resource) || isNotice(resource);
    }

    @Override
    public void processResource(String resource, InputStream inputStream, List<Relocator> relocators) throws IOException {
        processResource(resource, inputStream, relocators, Long.MIN_VALUE);
    }

    @Override
    public void processResource(String resource, InputStream inputStream, List<Relocator> relocators, long time)
            throws IOException {
        var contents = inputStream.readAllBytes();
        if (isLicense(resource)) {
            addUnique(licenses, contents);
            licenseTime = Math.max(licenseTime, time);
        } else if (isNotice(resource)) {
            addUnique(notices, contents);
            noticeTime = Math.max(noticeTime, time);
        }
    }

    @Override
    public boolean hasTransformedResource() {
        return !licenses.isEmpty() || !notices.isEmpty();
    }

    @Override
    public void modifyOutputStream(JarOutputStream jarOutputStream) throws IOException {
        writeResource(jarOutputStream, LICENSE_PATH, licenses, licenseTime);
        writeResource(jarOutputStream, NOTICE_PATH, notices, noticeTime);

        licenses.clear();
        notices.clear();
        licenseTime = Long.MIN_VALUE;
        noticeTime = Long.MIN_VALUE;
    }

    private static boolean isLicense(String resource) {
        return LICENSE_PATH.equalsIgnoreCase(resource)
                || "META-INF/LICENSE.txt".equalsIgnoreCase(resource)
                || "META-INF/LICENSE.md".equalsIgnoreCase(resource);
    }

    private static boolean isNotice(String resource) {
        return NOTICE_PATH.equalsIgnoreCase(resource)
                || "META-INF/NOTICE.txt".equalsIgnoreCase(resource)
                || "META-INF/NOTICE.md".equalsIgnoreCase(resource);
    }

    private static void addUnique(List<byte[]> resources, byte[] contents) {
        if (resources.stream().noneMatch(existing -> Arrays.equals(existing, contents))) {
            resources.add(contents);
        }
    }

    private static void writeResource(JarOutputStream jarOutputStream, String path, List<byte[]> resources, long time)
            throws IOException {
        if (resources.isEmpty()) {
            return;
        }

        var jarEntry = new JarEntry(path);
        if (time != Long.MIN_VALUE) {
            jarEntry.setTime(time);
        }
        jarOutputStream.putNextEntry(jarEntry);

        for (int i = 0; i < resources.size(); i++) {
            if (i > 0) {
                jarOutputStream.write('\n');
            }

            var contents = resources.get(i);
            jarOutputStream.write(contents);
            if (contents.length == 0 || contents[contents.length - 1] != '\n') {
                jarOutputStream.write('\n');
            }
        }

        jarOutputStream.closeEntry();
    }
}
