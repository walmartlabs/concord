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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LogbackManifestResourceTransformerTest {

    @Test
    void addsLogbackPackageVersionsFromDependencyManifests() throws Exception {
        var transformer = new LogbackManifestResourceTransformer();
        transformer.setMainClass("com.example.Main");

        transformer.processResource("META-INF/MANIFEST.MF", manifest("""
                Manifest-Version: 1.0
                Created-By: test
                
                """), List.of(), 1);

        transformer.processResource("META-INF/MANIFEST.MF", manifest("""
                Manifest-Version: 1.0
                Bundle-SymbolicName: ch.qos.logback.classic
                Implementation-Version: 1.2.3
                
                """), List.of(), 2);

        transformer.processResource("META-INF/MANIFEST.MF", manifest("""
                Manifest-Version: 1.0
                Bundle-SymbolicName: ch.qos.logback.core
                Implementation-Version: 4.5.6
                
                """), List.of(), 3);

        var result = resultManifest(transformer);

        assertEquals("com.example.Main", result.getMainAttributes().getValue("Main-Class"));
        assertEquals("1.2.3", result.getAttributes("ch/qos/logback/classic/util/").getValue("Implementation-Version"));
        assertEquals("4.5.6", result.getAttributes("ch/qos/logback/core/util/").getValue("Implementation-Version"));
    }

    @Test
    void addsLogbackPackageVersionsFromBundleSymbolicNamesWithDirectives() throws Exception {
        var transformer = new LogbackManifestResourceTransformer();

        transformer.processResource("META-INF/MANIFEST.MF", manifest("""
                Manifest-Version: 1.0
                Bundle-SymbolicName: ch.qos.logback.classic;singleton:=true
                Implementation-Version: 1.2.3

                """), List.of(), 1);
        transformer.processResource("META-INF/MANIFEST.MF", manifest("""
                Manifest-Version: 1.0
                Bundle-SymbolicName: ch.qos.logback.core; singleton:=true
                Implementation-Version: 4.5.6

                """), List.of(), 2);

        var result = resultManifest(transformer);

        assertEquals("1.2.3", result.getAttributes("ch/qos/logback/classic/util/").getValue("Implementation-Version"));
        assertEquals("4.5.6", result.getAttributes("ch/qos/logback/core/util/").getValue("Implementation-Version"));
    }

    @Test
    void preservesExistingLogbackPackageAttributes() throws Exception {
        var transformer = new LogbackManifestResourceTransformer();

        transformer.processResource("META-INF/MANIFEST.MF", manifest("""
                Manifest-Version: 1.0

                Name: ch/qos/logback/classic/util/
                Specification-Version: 1.0

                """), List.of(), 1);
        transformer.processResource("META-INF/MANIFEST.MF", manifest("""
                Manifest-Version: 1.0
                Bundle-SymbolicName: ch.qos.logback.classic
                Implementation-Version: 1.2.3

                """), List.of(), 2);

        var result = resultManifest(transformer);
        var attributes = result.getAttributes("ch/qos/logback/classic/util/");

        assertEquals("1.0", attributes.getValue("Specification-Version"));
        assertEquals("1.2.3", attributes.getValue("Implementation-Version"));
    }

    @Test
    void writesManifestWhenNoInputManifestWasProcessed() throws Exception {
        var transformer = new LogbackManifestResourceTransformer();
        transformer.setMainClass("com.example.Main");

        var result = resultManifest(transformer);

        assertEquals("1.0", result.getMainAttributes().getValue("Manifest-Version"));
        assertEquals("com.example.Main", result.getMainAttributes().getValue("Main-Class"));
    }

    @Test
    void legacyProcessResourceOverloadDoesNotForceEpochTimestamp() throws Exception {
        var transformer = new LogbackManifestResourceTransformer();

        transformer.processResource("META-INF/MANIFEST.MF", manifest("""
                Manifest-Version: 1.0

                """), List.of());

        assertNotEquals(0, resultManifestEntryTime(transformer));
    }

    private static ByteArrayInputStream manifest(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static Manifest resultManifest(LogbackManifestResourceTransformer transformer) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var jar = new JarOutputStream(bytes)) {
            transformer.modifyOutputStream(jar);
        }

        try (var jar = new JarInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return jar.getManifest();
        }
    }

    private static long resultManifestEntryTime(LogbackManifestResourceTransformer transformer) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var jar = new JarOutputStream(bytes)) {
            transformer.modifyOutputStream(jar);
        }

        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return zip.getNextEntry().getTime();
        }
    }
}
