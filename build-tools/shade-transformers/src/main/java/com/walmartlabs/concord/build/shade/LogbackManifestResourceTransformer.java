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
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class LogbackManifestResourceTransformer implements ReproducibleResourceTransformer {

    private static final String MANIFEST = "META-INF/MANIFEST.MF";

    private static final String LOGBACK_CLASSIC = "ch.qos.logback.classic";

    private static final String LOGBACK_CORE = "ch.qos.logback.core";

    private String mainClass;

    private Map<String, Object> manifestEntries;

    private boolean manifestDiscovered;

    private Manifest manifest;

    private long time = Long.MIN_VALUE;

    private String logbackClassicVersion;

    private String logbackCoreVersion;

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public void setManifestEntries(Map<String, Object> manifestEntries) {
        this.manifestEntries = manifestEntries;
    }

    @Override
    public boolean canTransformResource(String resource) {
        return MANIFEST.equalsIgnoreCase(resource);
    }

    @Override
    public void processResource(String resource, InputStream inputStream, List<Relocator> relocators) throws IOException {
        processResource(resource, inputStream, relocators, 0);
    }

    @Override
    public void processResource(String resource, InputStream inputStream, List<Relocator> relocators, long time) throws IOException {
        var current = new Manifest(inputStream);

        if (!manifestDiscovered) {
            manifest = current;
            manifestDiscovered = true;
        }

        var attributes = current.getMainAttributes();
        var bundleName = attributes.getValue("Bundle-SymbolicName");
        var implementationVersion = attributes.getValue("Implementation-Version");

        if (LOGBACK_CLASSIC.equals(bundleName)) {
            logbackClassicVersion = implementationVersion;
        } else if (LOGBACK_CORE.equals(bundleName)) {
            logbackCoreVersion = implementationVersion;
        }

        if (time > this.time) {
            this.time = time;
        }
    }

    @Override
    public boolean hasTransformedResource() {
        return true;
    }

    @Override
    public void modifyOutputStream(JarOutputStream jarOutputStream) throws IOException {
        if (manifest == null) {
            manifest = new Manifest();
        }

        var mainAttributes = manifest.getMainAttributes();
        if (mainAttributes.getValue(Attributes.Name.MANIFEST_VERSION) == null) {
            mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        }

        if (mainClass != null) {
            mainAttributes.put(Attributes.Name.MAIN_CLASS, mainClass);
        }

        if (manifestEntries != null) {
            for (var entry : manifestEntries.entrySet()) {
                mainAttributes.put(new Attributes.Name(entry.getKey()), entry.getValue());
            }
        }

        addLogbackPackageVersion("ch/qos/logback/classic/util/", logbackClassicVersion);
        addLogbackPackageVersion("ch/qos/logback/core/util/", logbackCoreVersion);

        var jarEntry = new JarEntry(MANIFEST);
        jarEntry.setTime(time);
        jarOutputStream.putNextEntry(jarEntry);
        manifest.write(jarOutputStream);
    }

    private void addLogbackPackageVersion(String packageName, String version) {
        if (version == null) {
            return;
        }

        var attributes = new Attributes();
        attributes.putValue("Implementation-Version", version);
        manifest.getEntries().put(packageName, attributes);
    }
}
