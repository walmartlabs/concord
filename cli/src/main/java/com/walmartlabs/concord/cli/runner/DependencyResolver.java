package com.walmartlabs.concord.cli.runner;

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

import com.walmartlabs.concord.dependencymanager.DependencyEntity;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.dependencymanager.ProgressListener;

import java.io.IOException;
import java.net.*;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.walmartlabs.concord.dependencymanager.DependencyManager.MAVEN_SCHEME;

public class DependencyResolver {

    private final DependencyManager dependencyManager;
    private final List<URI> defaultDependencies = Collections.emptyList();
    private final boolean verbose;

    public DependencyResolver(DependencyManager dependencyManager, boolean verbose) {
        this.dependencyManager = dependencyManager;
        this.verbose = verbose;
    }

    public Collection<String> resolveDeps(List<String> dependencies) throws Exception {
        if (verbose) {
            System.out.println("Resolving process dependencies...");
        }

        long t1 = System.currentTimeMillis();

        // combine the default dependencies and the process' dependencies
        Collection<URI> uris = Stream.concat(defaultDependencies.stream(),
                normalizeUrls(dependencies).stream())
                .collect(Collectors.toList());

        Collection<DependencyEntity> deps = dependencyManager.resolve(uris, new ProgressListener() {

            @Override
            public void onRetry(int retryCount, int maxRetry, long interval, String cause) {
                System.err.println("Error while downloading dependencies: " + cause);
                System.err.println("Retrying in " + interval + "ms");
            }

            @Override
            public void onTransferFailed(String error) {
                // when we have more than one repo in mvn.json we can get transfer error for one repo
                // but artifact will be resolved with second repo...
                if (verbose) {
                    System.err.println("Transfer failed: " + error);
                }
            }
        });

        // sort dependencies to maintain consistency in runner configurations
        Collection<String> paths = deps.stream()
                .map(DependencyEntity::getPath)
                .map(p -> p.toAbsolutePath().toString())
                .sorted()
                .collect(Collectors.toList());

        long t2 = System.currentTimeMillis();

        if (verbose) {
            System.out.println("Dependency resolution took " + ((t2 - t1)) + "ms");
            logDependencies(paths);
        }

        return paths;
    }

    private void logDependencies(Collection<?> deps) {
        if (verbose && deps.isEmpty()) {
            System.out.println("No external dependencies.");
            return;
        }

        List<String> l = deps.stream()
                .map(Object::toString)
                .collect(Collectors.toList());

        StringBuilder b = new StringBuilder();
        for (String s : l) {
            b.append("\n\t").append(s);
        }

        System.out.println("Dependencies: " + b);
    }

    private static Collection<URI> normalizeUrls(Collection<String> urls) throws IOException, URISyntaxException {
        if (urls == null || urls.isEmpty()) {
            return Collections.emptySet();
        }

        Collection<URI> result = new HashSet<>();

        for (String s : urls) {
            URI u = new URI(s);
            String scheme = u.getScheme();

            if (MAVEN_SCHEME.equalsIgnoreCase(scheme)) {
                result.add(u);
                continue;
            }

            if (scheme == null || scheme.trim().isEmpty()) {
                throw new IOException("Invalid dependency URL. Missing URL scheme: " + s);
            }

            if (s.endsWith(".jar")) {
                result.add(u);
                continue;
            }

            URL url = u.toURL();
            while (true) {
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    URLConnection conn = url.openConnection();
                    if (conn instanceof HttpURLConnection) {
                        HttpURLConnection httpConn = (HttpURLConnection) conn;
                        httpConn.setInstanceFollowRedirects(false);

                        int code = httpConn.getResponseCode();
                        if (code == HttpURLConnection.HTTP_MOVED_TEMP ||
                                code == HttpURLConnection.HTTP_MOVED_PERM ||
                                code == HttpURLConnection.HTTP_SEE_OTHER ||
                                code == 307) {

                            String location = httpConn.getHeaderField("Location");
                            url = new URL(location);
                            System.out.println("normalizeUrls -> using: " + location);

                            continue;
                        }

                        u = url.toURI();
                    } else {
                        System.out.println("normalizeUrls -> unexpected connection type: " + conn.getClass() + " (for " + s + ")");
                    }
                }

                break;
            }

            result.add(u);
        }

        return result;
    }
}
