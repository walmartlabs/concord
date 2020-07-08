package com.walmartlabs.concord.dependencymanager;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ExceptionUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.*;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class DependencyManager {

    private static final Logger log = LoggerFactory.getLogger(DependencyManager.class);

    private static final String CFG_FILE_KEY = "CONCORD_MAVEN_CFG";

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    private static final String FILES_CACHE_DIR = "files";
    public static final String MAVEN_SCHEME = "mvn";

    private static final MavenRepository MAVEN_CENTRAL = MavenRepository.builder()
            .id("central")
            .contentType("default")
            .url("https://repo.maven.apache.org/maven2/")
            .snapshotPolicy(MavenRepositoryPolicy.builder()
                    .enabled(false)
                    .build())
            .build();

    private static final List<MavenRepository> DEFAULT_REPOS = Collections.singletonList(MAVEN_CENTRAL);

    private final Path cacheDir;
    private final Path localCacheDir;
    private final List<RemoteRepository> repositories;
    private final Object mutex = new Object();
    private final RepositorySystem maven = newMavenRepositorySystem();

    public DependencyManager(Path cacheDir) throws IOException {
        this(cacheDir, getRepositories());
    }

    public DependencyManager(Path cacheDir, Path cfgFile) throws IOException {
        this(cacheDir, readCfg(cfgFile));
    }

    public DependencyManager(Path cacheDir, List<MavenRepository> repositories) throws IOException {
        this.cacheDir = cacheDir;
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }
        this.localCacheDir = Paths.get(System.getProperty("user.home")).resolve(".m2/repository");

        log.info("init -> using repositories: {}", repositories);
        this.repositories = toRemote(repositories);
    }

    public Collection<DependencyEntity> resolve(Collection<URI> items) throws IOException {
        return resolve(items, null);
    }

    public Collection<DependencyEntity> resolve(Collection<URI> items, ProgressListener listener) throws IOException {
        ResolveExceptionConverter exceptionConverter = new ResolveExceptionConverter(items);
        return withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> tryResolve(items), exceptionConverter, new ProgressNotifier(listener, exceptionConverter));
    }

    public DependencyEntity resolveSingle(URI item) throws IOException {
        return resolveSingle(item, null);
    }

    public DependencyEntity resolveSingle(URI item, ProgressListener listener) throws IOException {
        ResolveExceptionConverter exceptionConverter = new ResolveExceptionConverter(item);
        return withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> tryResolveSingle(item), exceptionConverter, new ProgressNotifier(listener, exceptionConverter));
    }

    private Collection<DependencyEntity> tryResolve(Collection<URI> items) throws IOException {
        if (items == null || items.isEmpty()) {
            return Collections.emptySet();
        }

        // ensure stable order
        List<URI> uris = new ArrayList<>(items);
        Collections.sort(uris);

        DependencyList deps = categorize(uris);

        Collection<DependencyEntity> result = new HashSet<>();

        result.addAll(resolveDirectLinks(deps.directLinks));

        result.addAll(resolveMavenTransitiveDependencies(deps.mavenTransitiveDependencies).stream()
                .map(DependencyManager::toDependency)
                .collect(Collectors.toList()));

        result.addAll(resolveMavenSingleDependencies(deps.mavenSingleDependencies).stream()
                .map(DependencyManager::toDependency)
                .collect(Collectors.toList()));

        return result;
    }

    private DependencyEntity tryResolveSingle(URI item) throws IOException {
        String scheme = item.getScheme();
        if (MAVEN_SCHEME.equalsIgnoreCase(scheme)) {
            String id = item.getAuthority();
            Artifact artifact = resolveMavenSingle(new MavenDependency(new DefaultArtifact(id), JavaScopes.COMPILE));
            return toDependency(artifact);
        } else {
            return new DependencyEntity(resolveFile(item), item);
        }
    }

    private DependencyList categorize(List<URI> items) throws IOException {
        List<MavenDependency> mavenTransitiveDependencies = new ArrayList<>();
        List<MavenDependency> mavenSingleDependencies = new ArrayList<>();
        List<URI> directLinks = new ArrayList<>();

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

    private Collection<DependencyEntity> resolveDirectLinks(Collection<URI> items) throws IOException {
        Collection<DependencyEntity> paths = new HashSet<>();
        for (URI item : items) {
            paths.add(new DependencyEntity(resolveFile(item), item));
        }
        return paths;
    }

    private Path resolveFile(URI uri) throws IOException {
        boolean skipCache = shouldSkipCache(uri);
        String name = getLastPart(uri);

        Path baseDir = cacheDir.resolve(FILES_CACHE_DIR).resolve(hash(uri.toString()));
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }

        Path dst = baseDir.resolve(name);

        synchronized (mutex) {
            if (!skipCache && Files.exists(dst)) {
                log.info("resolveFile -> using a cached copy of {}...", uri);
                return dst;
            }

            log.info("resolveFile -> downloading {}...", uri);

            Path tmp = baseDir.resolve(name + ".tmp");
            try {
                download(uri, tmp);
                Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                if (Files.exists(tmp)) {
                    Files.delete(tmp);
                }
            }


            return dst;
        }
    }

    private static Path getConfigFileLocation() {
        String s = System.getenv(CFG_FILE_KEY);
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return Paths.get(s);
    }

    private static List<MavenRepository> getRepositories() {
        Path src = getConfigFileLocation();
        if (src == null) {
            return DEFAULT_REPOS;
        }
        return readCfg(src);
    }

    private static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(s.getBytes());
            return DatatypeConverter.printHexBinary(md.digest()).toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("Hash error", e);
        }
    }

    private Artifact resolveMavenSingle(MavenDependency dep) throws IOException {
        RepositorySystemSession session = newRepositorySystemSession(maven);

        ArtifactRequest req = new ArtifactRequest();
        req.setArtifact(dep.artifact);
        req.setRepositories(repositories);

        synchronized (mutex) {
            try {
                ArtifactResult r = maven.resolveArtifact(session, req);
                return r.getArtifact();
            } catch (ArtifactResolutionException e) {
                throw new IOException(e);
            }
        }
    }

    private Collection<Artifact> resolveMavenSingleDependencies(Collection<MavenDependency> deps) throws IOException {
        Collection<Artifact> paths = new HashSet<>();
        for (MavenDependency dep : deps) {
            paths.add(resolveMavenSingle(dep));
        }
        return paths;
    }

    private Collection<Artifact> resolveMavenTransitiveDependencies(Collection<MavenDependency> deps) throws IOException {
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
                return system.resolveDependencies(session, dependencyRequest)
                        .getArtifactResults().stream()
                        .map(ArtifactResult::getArtifact)
                        .collect(Collectors.toSet());
            } catch (DependencyResolutionException e) {
                throw new IOException(e);
            }
        }
    }

    private DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_IGNORE);

        LocalRepository localRepo = new LocalRepository(localCacheDir.toFile());
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

    private static DependencyEntity toDependency(Artifact artifact) {
        return new DependencyEntity(artifact.getFile().toPath(),
                artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
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
                .map(DependencyManager::toRemote)
                .collect(Collectors.toList());
    }

    private static RemoteRepository toRemote(MavenRepository r) {
        RemoteRepository.Builder b = new RemoteRepository.Builder(r.id(), r.contentType(), r.url());

        MavenRepositoryPolicy releasePolicy = r.releasePolicy();
        if (releasePolicy != null) {
            b.setReleasePolicy(new RepositoryPolicy(releasePolicy.enabled(), releasePolicy.updatePolicy(), releasePolicy.checksumPolicy()));
        }

        MavenRepositoryPolicy snapshotPolicy = r.snapshotPolicy();
        if (snapshotPolicy != null) {
            b.setSnapshotPolicy(new RepositoryPolicy(snapshotPolicy.enabled(), snapshotPolicy.updatePolicy(), snapshotPolicy.checksumPolicy()));
        }

        Map<String, String> auth = r.auth();
        if (auth != null) {
            AuthenticationBuilder ab = new AuthenticationBuilder();
            auth.forEach(ab::addString);
            b.setAuthentication(ab.build());
        }

        MavenProxy proxy = r.proxy();
        if (proxy != null) {
            b.setProxy(new Proxy(proxy.type(), proxy.host(), proxy.port()));
        }

        return b.build();
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

    private static List<MavenRepository> readCfg(Path src) {
        src = src.toAbsolutePath().normalize();

        if (!Files.exists(src)) {
            log.warn("readCfg -> file not found: {}, using the default repos", src);
            return DEFAULT_REPOS;
        }

        ObjectMapper om = new ObjectMapper();
        try (InputStream in = Files.newInputStream(src)) {
            MavenRepositoryConfiguration cfg = om.readValue(in, MavenRepositoryConfiguration.class);
            return cfg.repositories();
        } catch (IOException e) {
            throw new RuntimeException("Error while reading the Maven configuration file: " + src, e);
        }
    }

    private static final class DependencyList {

        private final List<MavenDependency> mavenTransitiveDependencies;
        private final List<MavenDependency> mavenSingleDependencies;
        private final List<URI> directLinks;

        private DependencyList(List<MavenDependency> mavenTransitiveDependencies,
                               List<MavenDependency> mavenSingleDependencies,
                               List<URI> directLinks) {

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

    private static class ProgressNotifier implements RetryUtils.RetryListener {

        private final ProgressListener listener;
        private final ResolveExceptionConverter exceptionConverter;

        private ProgressNotifier(ProgressListener listener, ResolveExceptionConverter exceptionConverter) {
            this.listener = listener;
            this.exceptionConverter = exceptionConverter;
        }

        @Override
        public void onRetry(int tryCount, int retryCount, long retryInterval, Exception e) {
            if (listener != null) {
                DependencyManagerException ex = exceptionConverter.convert(e);
                listener.onRetry(tryCount, retryCount, retryInterval, ex.getMessage());
            }
        }
    }

    public static <T> T withRetry(int retryCount, long retryInterval, Callable<T> c,
                                  ResolveExceptionConverter exceptionConverter,
                                  ProgressNotifier notifier) throws IOException {
        try {
            return RetryUtils.withRetry(retryCount, retryInterval, c, ResolveRetryStrategy.INSTANCE, notifier);
        } catch (Exception e) {
            log.error("resolve exception: ", e);
            throw exceptionConverter.convert(e);
        }
    }

    private static class ResolveRetryStrategy implements RetryUtils.RetryStrategy {

        public static final ResolveRetryStrategy INSTANCE = new ResolveRetryStrategy();

        @Override
        public boolean canRetry(Exception e) {
            List<Throwable> exceptions = ExceptionUtils.getExceptionList(e);
            Throwable last = exceptions.get(exceptions.size() - 1);
            return !(last instanceof ArtifactNotFoundException);
        }
    }
}
