package com.walmartlabs.concord.dependencymanager;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.*;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

import static org.eclipse.aether.repository.RepositoryPolicy.CHECKSUM_POLICY_IGNORE;
import static org.eclipse.aether.repository.RepositoryPolicy.UPDATE_POLICY_NEVER;

public class DependencyManager {

    private static final Logger log = LoggerFactory.getLogger(DependencyManager.class);

    public static final String MAVEN_SCHEME = "mvn";
    public static final String CFG_FILE_KEY = "CONCORD_MAVEN_CFG";

    private static final String FILES_CACHE_DIR = "files";
    private static final String MAVEN_CACHE_DIR = "maven";

    private static final MavenRepository WALMART_WARM = new MavenRepository("warm", "default", "https://nexus.prod.walmart.com/nexus/content/groups/public/", false);
    private static final MavenRepository MAVEN_CENTRAL = new MavenRepository("central", "default", "https://repo.maven.apache.org/maven2/", false);
    private static final MavenRepository LOCAL_M2 = new MavenRepository("local", "default", "file://" + System.getProperty("user.home") + "/.m2/repository", true);
    private static final List<MavenRepository> DEFAULT_REPOS = Arrays.asList(LOCAL_M2, WALMART_WARM, MAVEN_CENTRAL);

    private final Path cacheDir;
    private final List<RemoteRepository> repositories;
    private final Object mutex = new Object();
    private final RepositorySystem maven = newMavenRepositorySystem();
    private final RepositoryCache mavenCache = new DefaultRepositoryCache();

    public DependencyManager(Path cacheDir) {
        this(cacheDir, readCfg());
    }

    public DependencyManager(Path cacheDir, List<MavenRepository> repositories) {
        this.cacheDir = cacheDir;

        log.info("init -> using repositories: {}", repositories);
        this.repositories = toRemote(repositories);
    }

    public Collection<Path> resolve(Collection<URI> items) throws IOException {
        if (items == null || items.isEmpty()) {
            return Collections.emptySet();
        }

        DependencyList deps = categorize(items);

        Collection<Path> paths = new HashSet<>();
        paths.addAll(resolveDirectLinks(deps.directLinks));
        paths.addAll(resolveMavenTransitiveDependencies(deps.mavenTransitiveDependencies));
        paths.addAll(resolveMavenSingleDependencies(deps.mavenSingleDependencies));

        return paths;
    }

    private DependencyList categorize(Collection<URI> items) throws IOException {
        Set<MavenDependency> mavenTransitiveDependencies = new HashSet<>();
        Set<MavenDependency> mavenSingleDependencies = new HashSet<>();
        Set<URI> directLinks = new HashSet<>();

        for (URI item : items) {
            String scheme = item.getScheme();
            if (MAVEN_SCHEME.equalsIgnoreCase(scheme)) {
                String id = item.getAuthority();
                Artifact artifact = new DefaultArtifact(id);

                Map<String, String> cfg = splitQuery(item);
                String scope = cfg.getOrDefault("scope", JavaScopes.COMPILE);
                boolean transitive = Boolean.parseBoolean(cfg.getOrDefault("transitive", "true"));

                if (transitive) {
                    mavenTransitiveDependencies.add(new MavenDependency(artifact, scope));
                } else {
                    mavenSingleDependencies.add(new MavenDependency(artifact, scope));
                }
            } else {
                directLinks.add(item);
            }
        }

        return new DependencyList(mavenTransitiveDependencies, mavenSingleDependencies, directLinks);
    }

    public Path resolveSingle(URI item) throws IOException {
        String scheme = item.getScheme();
        if (MAVEN_SCHEME.equalsIgnoreCase(scheme)) {
            String id = item.getAuthority();
            Artifact artifact = new DefaultArtifact(id);
            return resolveMavenSingle(new MavenDependency(artifact, JavaScopes.COMPILE));
        } else {
            return resolveFile(item);
        }
    }

    private Collection<Path> resolveDirectLinks(Collection<URI> items) throws IOException {
        Collection<Path> paths = new HashSet<>();
        for (URI item : items) {
            paths.add(resolveFile(item));
        }
        return paths;
    }

    private Path resolveFile(URI uri) throws IOException {
        boolean skipCache = shouldSkipCache(uri);
        String name = getLastPart(uri);

        Path baseDir = cacheDir.resolve(FILES_CACHE_DIR);
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }

        Path p = baseDir.resolve(name);

        synchronized (mutex) {
            if (skipCache || !Files.exists(p)) {
                log.info("resolveFile -> downloading {}...", uri);
                download(uri, p);
            }

            return p;
        }
    }

    private Path resolveMavenSingle(MavenDependency dep) throws IOException {
        RepositorySystemSession session = newRepositorySystemSession(maven);

        ArtifactRequest req = new ArtifactRequest();
        req.setArtifact(dep.artifact);
        req.setRepositories(repositories);

        synchronized (mutex) {
            try {
                ArtifactResult r = maven.resolveArtifact(session, req);
                return r.getArtifact().getFile().toPath();
            } catch (ArtifactResolutionException e) {
                throw new IOException(e);
            }
        }
    }

    private Collection<Path> resolveMavenSingleDependencies(Collection<MavenDependency> deps) throws IOException {
        Collection<Path> paths = new HashSet<>();
        for (MavenDependency dep : deps) {
            paths.add(resolveMavenSingle(dep));
        }
        return paths;
    }

    private Collection<Path> resolveMavenTransitiveDependencies(Collection<MavenDependency> deps) throws IOException {
        RepositorySystem system = newMavenRepositorySystem();
        RepositorySystemSession session = newRepositorySystemSession(system);

        CollectRequest req = new CollectRequest();
        req.setDependencies(deps.stream()
                .map(d -> new Dependency(d.artifact, d.scope))
                .collect(Collectors.toList()));
        req.setRepositories(repositories);

        DependencyRequest dependencyRequest = new DependencyRequest(req, null);

        synchronized (mutex) {
            try {
                Collection<ArtifactResult> r = system.resolveDependencies(session, dependencyRequest)
                        .getArtifactResults();

                return r.stream()
                        .map(a -> a.getArtifact().getFile().toPath())
                        .collect(Collectors.toSet());
            } catch (DependencyResolutionException e) {
                throw new IOException(e);
            }
        }
    }

    private DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) throws IOException {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setCache(mavenCache);
        session.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_IGNORE);

        Path baseDir = cacheDir.resolve(MAVEN_CACHE_DIR);
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }

        LocalRepository localRepo = new LocalRepository(baseDir.toFile());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        session.setTransferListener(new AbstractTransferListener() {
            @Override
            public void transferFailed(TransferEvent event) {
                log.error("transferFailed -> {}", event);
            }
        });
        session.setRepositoryListener(new AbstractRepositoryListener() {
            @Override
            public void artifactResolving(RepositoryEvent event) {
                log.debug("artifactResolving -> {}", event);
            }

            @Override
            public void artifactResolved(RepositoryEvent event) {
                log.debug("artifactResolved -> {}", event);
            }
        });

        return session;
    }

    private static void download(URI uri, Path dst) throws IOException {
        try (InputStream in = uri.toURL().openStream();
             OutputStream out = Files.newOutputStream(dst, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            byte[] ab = new byte[4096];
            int read;
            while ((read = in.read(ab)) > 0) {
                out.write(ab, 0, read);
            }
        }
    }

    private static String getLastPart(URI uri) {
        String p = uri.getPath();
        int idx = p.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < p.length()) {
            return p.substring(idx + 1);
        }
        throw new IllegalArgumentException("Invalid dependency URL. Can't get a file name: " + uri);
    }

    private static boolean shouldSkipCache(URI u) {
        return "file".equalsIgnoreCase(u.getScheme()) || u.getPath().contains("SNAPSHOT");
    }

    private static RepositorySystem newMavenRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                log.error("newMavenRepositorySystem -> service creation error: type={}, impl={}", type, impl, exception);
            }
        });

        return locator.getService(RepositorySystem.class);
    }

    private static List<RemoteRepository> toRemote(List<MavenRepository> l) {
        return l.stream()
                .map(r -> {
                    RemoteRepository.Builder result = new RemoteRepository.Builder(r.getId(), r.getContentType(), r.getUrl());
                    if (!r.isSnapshotEnabled()) {
                        result.setSnapshotPolicy(new RepositoryPolicy(false, UPDATE_POLICY_NEVER, CHECKSUM_POLICY_IGNORE));
                    }
                    return result.build();
                })
                .collect(Collectors.toList());
    }

    private static Map<String, String> splitQuery(URI uri) throws UnsupportedEncodingException {
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> m = new LinkedHashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            String k = pair.substring(0, idx);
            String v = pair.substring(idx + 1);
            m.put(URLDecoder.decode(k, "UTF-8"), URLDecoder.decode(v, "UTF-8"));
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static List<MavenRepository> readCfg() {
        String s = System.getenv(CFG_FILE_KEY);
        if (s == null || s.trim().isEmpty()) {
            return DEFAULT_REPOS;
        }

        Path p = Paths.get(s);
        if (!Files.exists(p)) {
            log.warn("readCfg -> file not found: {}, using the default repos", s);
            return DEFAULT_REPOS;
        }

        Map<String, Object> m;
        try (InputStream in = Files.newInputStream(p)) {
            ObjectMapper om = new ObjectMapper();
            m = om.readValue(in, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("Error while reading the Maven configuration file: " + s, e);
        }

        Object v = m.get("repositories");
        if (v == null) {
            return DEFAULT_REPOS;
        }

        if (!(v instanceof Collection)) {
            throw new RuntimeException("The 'repositories' value should be an array of objects: " + s);
        }

        Collection<Map<String, Object>> repos = (Collection<Map<String, Object>>) v;

        List<MavenRepository> l = new ArrayList<>();
        for (Map<String, Object> r : repos) {
            String id = (String) r.get("id");
            if (id == null) {
                throw new RuntimeException("Missing repository 'id' value: " + s);
            }

            String contentType = (String) r.getOrDefault("layout", "default");

            String url = (String) r.get("url");
            if (url == null) {
                throw new RuntimeException("Missing repository 'url' value: " + s);
            }

            l.add(new MavenRepository(id, contentType, url, true));
        }
        return l;
    }

    private static final class DependencyList {

        private final Set<MavenDependency> mavenTransitiveDependencies;
        private final Set<MavenDependency> mavenSingleDependencies;
        private final Set<URI> directLinks;

        private DependencyList(Set<MavenDependency> mavenTransitiveDependencies,
                               Set<MavenDependency> mavenSingleDependencies,
                               Set<URI> directLinks) {

            this.mavenTransitiveDependencies = mavenTransitiveDependencies;
            this.mavenSingleDependencies = mavenSingleDependencies;
            this.directLinks = directLinks;
        }
    }

    private static final class MavenDependency {

        private final Artifact artifact;
        private final String scope;

        private MavenDependency(Artifact artifact, String scope) {
            this.artifact = artifact;
            this.scope = scope;
        }
    }
}
