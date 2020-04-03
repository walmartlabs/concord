package com.walmartlabs.concord.runtime.v2.runner.guice;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.google.inject.AbstractModule;
import com.walmartlabs.concord.runtime.v2.runner.InjectorFactory;
import com.walmartlabs.concord.sdk.Constants;
import org.eclipse.sisu.space.SpaceModule;
import org.eclipse.sisu.space.URLClassSpace;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads the specified dependencies as Guice beans.
 */
public class ProcessDependenciesModule extends AbstractModule {

    private final Path workDir;
    private final Collection<String> dependencies;

    public ProcessDependenciesModule(Path workDir, Collection<String> dependencies) {
        this.workDir = workDir;
        this.dependencies = dependencies;
    }

    @Override
    protected void configure() {
        try {
            ClassLoader cl = loadDependencies(workDir, dependencies);
            install(new SpaceModule(new URLClassSpace(cl)));
        } catch (IOException e) {
            addError(e);
        }
    }

    private static URLClassLoader loadDependencies(Path workDir, Collection<String> dependencies) throws IOException {
        List<URL> urls = toURLs(workDir, dependencies);
        return new URLClassLoader(urls.toArray(new URL[0]), InjectorFactory.class.getClassLoader());
    }

    private static List<URL> toURLs(Path workDir, Collection<String> dependencies) throws IOException {
        List<URL> urls = dependencies.stream()
                .sorted()
                .map(s -> {
                    try {
                        // assume all dependencies are resolved into file paths at this point
                        return new URL("file://" + s);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException("Invalid dependency " + s + ": " + e.getMessage());
                    }
                }).collect(Collectors.toList());

        // collect all JARs from the `${workDir}/lib/` directory

        Path lib = workDir.resolve(Constants.Files.LIBRARIES_DIR_NAME);
        if (Files.exists(lib)) {
            try (Stream<Path> s = Files.list(lib).sorted()) {
                s.forEach(f -> {
                    if (f.toString().endsWith(".jar")) {
                        try {
                            urls.add(f.toUri().toURL());
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
        }

        return urls;
    }
}
