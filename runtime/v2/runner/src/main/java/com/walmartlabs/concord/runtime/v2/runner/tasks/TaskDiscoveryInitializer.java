package com.walmartlabs.concord.runtime.v2.runner.tasks;

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

import com.google.inject.ProvisionException;
import com.walmartlabs.concord.runtime.common.injector.InjectAnnotationUtils;
import com.walmartlabs.concord.runtime.common.injector.TaskHolder;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarFile;

public class TaskDiscoveryInitializer {

    private static final Logger log = LoggerFactory.getLogger(TaskDiscoveryInitializer.class);

    private static final String JAKARTA_SISU_INDEX = "META-INF/sisu/" + InjectAnnotationUtils.JAKARTA_NAMED;
    private static final byte[] JAKARTA_NAMED_DESCRIPTOR = "jakarta/inject/Named".getBytes(StandardCharsets.UTF_8);

    @Inject
    public TaskDiscoveryInitializer(TaskHolder<Task> holder,
                                    @Named("runtime") ClassLoader classLoader) {

        try {
            discover(holder, classLoader);
        } catch (IOException e) {
            throw new ProvisionException("Error while discovering Jakarta task classes", e);
        }
    }

    private static void discover(TaskHolder<Task> holder, ClassLoader classLoader) throws IOException {
        var classNames = new LinkedHashSet<String>();
        readJakartaSisuIndex(classLoader, classNames);
        scanJakartaNamedClasses(classLoader, classNames);

        for (var className : classNames) {
            register(holder, classLoader, className);
        }
    }

    private static void readJakartaSisuIndex(ClassLoader classLoader, Set<String> classNames) throws IOException {
        var resources = classLoader.getResources(JAKARTA_SISU_INDEX);
        while (resources.hasMoreElements()) {
            var resource = resources.nextElement();
            try (var in = resource.openStream();
                 var reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    var className = line.trim();
                    if (!className.isEmpty()) {
                        classNames.add(className);
                    }
                }
            }
        }
    }

    private static void scanJakartaNamedClasses(ClassLoader classLoader, Set<String> classNames) throws IOException {
        if (!(classLoader instanceof URLClassLoader urlClassLoader)) {
            return;
        }

        for (var url : urlClassLoader.getURLs()) {
            var path = toPath(url);
            if (path == null) {
                continue;
            }

            if (Files.isDirectory(path)) {
                scanDirectory(path, classNames);
            } else if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
                scanJar(path, classNames);
            }
        }
    }

    private static Path toPath(URL url) {
        if (!"file".equals(url.getProtocol())) {
            return null;
        }

        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            log.debug("Invalid classpath URL: {}", url, e);
            return null;
        }
    }

    private static void scanDirectory(Path dir, Set<String> classNames) throws IOException {
        try (var stream = Files.walk(dir)) {
            var files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .toList();

            for (var file : files) {
                var bytes = Files.readAllBytes(file);
                if (contains(bytes, JAKARTA_NAMED_DESCRIPTOR)) {
                    addDirectoryClassName(dir, file, classNames);
                }
            }
        }
    }

    private static void addDirectoryClassName(Path dir, Path file, Set<String> classNames) {
        var relativePath = dir.relativize(file).toString();
        if (relativePath.startsWith("META-INF")) {
            return;
        }

        classNames.add(relativePath
                .substring(0, relativePath.length() - ".class".length())
                .replace(File.separatorChar, '.'));
    }

    private static void scanJar(Path jarPath, Set<String> classNames) throws IOException {
        try (var jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/")) {
                    continue;
                }

                try (var in = jar.getInputStream(entry)) {
                    if (contains(in.readAllBytes(), JAKARTA_NAMED_DESCRIPTOR)) {
                        classNames.add(entry.getName()
                                .substring(0, entry.getName().length() - ".class".length())
                                .replace('/', '.'));
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void register(TaskHolder<Task> holder, ClassLoader classLoader, String className) {
        Class<?> klass;
        try {
            klass = Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            log.debug("Ignoring unloadable task class: {}", className, e);
            return;
        }

        if (!Task.class.isAssignableFrom(klass)) {
            return;
        }

        var name = InjectAnnotationUtils.findNamedValue(klass).orElse(null);
        if (name == null || name.isEmpty()) {
            log.warn("Ignoring task class without @Named: {}", klass);
            return;
        }

        if (InjectAnnotationUtils.hasSingleton(klass)) {
            log.warn("Ignoring task class with @Singleton: {}", klass);
            return;
        }

        var existing = holder.get(name);
        if (existing == klass) {
            return;
        }

        holder.add(name, (Class<Task>) klass);
    }

    private static boolean contains(byte[] value, byte[] pattern) {
        for (var i = 0; i <= value.length - pattern.length; i++) {
            var match = true;
            for (var j = 0; j < pattern.length; j++) {
                if (value[i + j] != pattern[j]) {
                    match = false;
                    break;
                }
            }

            if (match) {
                return true;
            }
        }
        return false;
    }
}
