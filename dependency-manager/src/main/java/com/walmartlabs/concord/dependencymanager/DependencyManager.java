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

import com.walmartlabs.concord.common.ExceptionUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.ExclusionsDependencyFilter;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Singleton
public class DependencyManager {

    private static final Logger log = LoggerFactory.getLogger(DependencyManager.class);

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    private static final String FILES_CACHE_DIR = "files";
    public static final String MAVEN_SCHEME = "mvn";
    private static final String LATEST = "LATEST";

    private final Path cacheDir;
    private final Path localCacheDir;
    private final List<RemoteRepository> repositories;
    private final Object mutex = new Object();
    private final RepositorySystem maven;
    private final boolean strictRepositories;

    private final List<String> defaultExclusions;

    private final boolean explicitlyResolveV1Client;
    private final boolean offlineMode;

    @Inject
    public DependencyManager(DependencyManagerConfiguration cfg) throws IOException {
        this.cacheDir = cfg.cacheDir();
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }
        this.localCacheDir = Paths.get(System.getProperty("user.home")).resolve(".m2/repository");

        log.info("init -> using repositories: {}", cfg.repositories());
        this.repositories = toRemote(cfg.repositories());
        this.maven = RepositorySystemFactory.create();
        this.strictRepositories = cfg.strictRepositories();
        this.defaultExclusions = cfg.exclusions();
        this.explicitlyResolveV1Client = cfg.explicitlyResolveV1Client();
        this.offlineMode = cfg.offlineMode();
    }

    public Collection<DependencyEntity> resolve(Collection<URI> items) throws IOException {
        return resolve(items, null);
    }

    public Collection<DependencyEntity> resolve(Collection<URI> items, ProgressListener listener) throws IOException {
        ResolveExceptionConverter exceptionConverter = new ResolveExceptionConverter(items);
        ProgressNotifier progressNotifier = new ProgressNotifier(listener, exceptionConverter);
        return withRetry(() -> tryResolve(items, progressNotifier), exceptionConverter, progressNotifier);
    }

    public DependencyEntity resolveSingle(URI item) throws IOException {
        return resolveSingle(item, null);
    }

    public DependencyEntity resolveSingle(URI item, ProgressListener listener) throws IOException {
        ResolveExceptionConverter exceptionConverter = new ResolveExceptionConverter(item);
        ProgressNotifier progressNotifier = new ProgressNotifier(listener, exceptionConverter);
        return withRetry(() -> tryResolveSingle(item, progressNotifier), exceptionConverter, progressNotifier);
    }

    private Collection<DependencyEntity> tryResolve(Collection<URI> items, ProgressNotifier progressNotifier) throws IOException {
        if (items == null || items.isEmpty()) {
            return Collections.emptySet();
        }

        // ensure stable order
        List<URI> uris = new ArrayList<>(new HashSet<>(items));
        Collections.sort(uris);

        DependencyList deps = categorize(uris);

        Collection<DependencyEntity> result = new HashSet<>();

        result.addAll(resolveDirectLinks(deps.directLinks));

        result.addAll(resolveMavenTransitiveDependencies(deps.mavenTransitiveDependencies, deps.mavenExclusions, progressNotifier).stream()
                .map(DependencyManager::toDependency)
                .toList());

        result.addAll(resolveMavenSingleDependencies(deps.mavenSingleDependencies, progressNotifier).stream()
                .map(DependencyManager::toDependency)
                .toList());

        return result;
    }

    private DependencyEntity tryResolveSingle(URI item, ProgressNotifier progressNotifier) throws IOException {
        String scheme = item.getScheme();
        if (MAVEN_SCHEME.equalsIgnoreCase(scheme)) {
            String id = item.getAuthority();
            Artifact artifact = resolveMavenSingle(new MavenDependency(new DefaultArtifact(id), JavaScopes.COMPILE), progressNotifier);
            return toDependency(artifact);
        } else {
            return new DependencyEntity(resolveFile(item), item);
        }
    }

    private DependencyList categorize(List<URI> items) throws IOException {
        List<MavenDependency> mavenTransitiveDependencies = new ArrayList<>();
        List<MavenDependency> mavenSingleDependencies = new ArrayList<>();
        List<String> mavenExclusions = new ArrayList<>();
        List<URI> directLinks = new ArrayList<>();

        for (URI item : items) {
            String scheme = item.getScheme();
            if (MAVEN_SCHEME.equalsIgnoreCase(scheme)) {
                String id = item.getAuthority();

                Artifact artifact = new DefaultArtifact(id);

                Map<String, List<String>> cfg = splitQuery(item);
                String scope = getSingleValue(cfg, "scope", JavaScopes.COMPILE);
                boolean transitive = Boolean.parseBoolean(getSingleValue(cfg, "transitive", "true"));

                if (transitive) {
                    mavenTransitiveDependencies.add(new MavenDependency(artifact, scope));
                } else {
                    mavenSingleDependencies.add(new MavenDependency(artifact, scope));
                }

                mavenExclusions.addAll(cfg.getOrDefault("exclude", Collections.emptyList()));
            } else {
                directLinks.add(item);
            }
        }

        return new DependencyList(mavenTransitiveDependencies, mavenSingleDependencies, mavenExclusions, directLinks);
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

            log.info("resolveFile -> downloading {}", uri);

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

    private static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(s.getBytes());
            return DatatypeConverter.printHexBinary(md.digest()).toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("Hash error", e);
        }
    }

    private Artifact resolveMavenSingle(MavenDependency dep, ProgressNotifier progressNotifier) throws IOException {
        RepositorySystemSession session = getRepositorySession(Collections.singletonList(dep), maven, progressNotifier);

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

    private RepositorySystemSession getRepositorySession(Collection<MavenDependency> mavenDependencies, RepositorySystem system, ProgressNotifier progressNotifier) {
        boolean isLatest = mavenDependencies.stream()
                .map(d -> d.artifact.getVersion())
                .anyMatch(a -> a.equals(LATEST));

        if (isLatest) {
            return newRepositorySystemSession(system, progressNotifier, true);
        }

        return newRepositorySystemSession(system, progressNotifier);
    }

    private Collection<Artifact> resolveMavenSingleDependencies(Collection<MavenDependency> deps, ProgressNotifier progressNotifier) throws IOException {
        Collection<Artifact> paths = new HashSet<>();
        for (MavenDependency dep : deps) {
            paths.add(resolveMavenSingle(dep, progressNotifier));
        }
        return paths;
    }

    private Collection<Artifact> resolveMavenTransitiveDependencies(Collection<MavenDependency> deps, List<String> exclusions, ProgressNotifier progressNotifier) throws IOException {
        // TODO: why we need new RepositorySystem?
        RepositorySystem system = RepositorySystemFactory.create();
        RepositorySystemSession session = getRepositorySession(deps, system, progressNotifier);

        CollectRequest req = new CollectRequest();
        req.setDependencies(deps.stream()
                .map(d -> new Dependency(d.artifact, d.scope))
                .collect(Collectors.toList()));
        req.setRepositories(repositories);

        List<String> excludes = new ArrayList<>(exclusions);
        excludes.addAll(defaultExclusions);

        DependencyRequest dependencyRequest = new DependencyRequest(req, new ExclusionsDependencyFilter(excludes));
        if (explicitlyResolveV1Client) {
            dependencyRequest.getCollectRequest().addManagedDependency(new Dependency(ClientDepSelector.CLIENT1_ARTIFACT, ""));
        }

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

    private DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system, ProgressNotifier progressNotifier) {
        return newRepositorySystemSession(system, progressNotifier, false);
    }

    private DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system, ProgressNotifier progressNotifier, boolean updatePolicy) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_IGNORE);
        session.setIgnoreArtifactDescriptorRepositories(strictRepositories);
        if (explicitlyResolveV1Client) {
            DependencySelector selector = new AndDependencySelector(
                    new ClientDepSelector(),
                    new OptionalDependencySelector(),
                    new ExclusionDependencySelector());

            session.setDependencySelector(selector);
        }

        LocalRepository localRepo = new LocalRepository(localCacheDir.toFile());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        session.setTransferListener(new AbstractTransferListener() {
            @Override
            public void transferFailed(TransferEvent event) {
                progressNotifier.transferFailed(event);
            }
        });

        session.setOffline(offlineMode);

        if (updatePolicy) {
            session.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        }

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

    private static String getSingleValue(Map<String, List<String>> m, String k, String defaultValue) {
        List<String> vv = m.get(k);
        if (vv == null || vv.isEmpty()) {
            return defaultValue;
        }
        return vv.get(0);
    }

    private static Map<String, List<String>> splitQuery(URI uri) throws UnsupportedEncodingException {
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> m = new LinkedHashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);

            List<String> vv = m.computeIfAbsent(k, s -> new ArrayList<>());
            vv.add(v);
            m.put(k, vv);
        }
        return m;
    }

    private static class ClientDepSelector implements DependencySelector {

        private static final String CONCORD_CLIENT_GROUP_ID = "com.walmartlabs.concord";
        private static final String CONCORD_CLIENT_ARTIFACT_ID = "concord-client";

        public static final Artifact CLIENT1_ARTIFACT = new DefaultArtifact(CONCORD_CLIENT_GROUP_ID, CONCORD_CLIENT_ARTIFACT_ID, "jar", Version.get());

        private final boolean transitive;
        private final Collection<String> excluded;

        public ClientDepSelector() {
            this(false, Arrays.asList("test", "provided"));
        }

        public ClientDepSelector(boolean transitive, Collection<String> excluded) {
            this.transitive = transitive;
            this.excluded = excluded;
        }

        @Override
        public boolean selectDependency(Dependency dependency) {
            if (CONCORD_CLIENT_GROUP_ID.equals(dependency.getArtifact().getGroupId()) &&
                CONCORD_CLIENT_ARTIFACT_ID.equals(dependency.getArtifact().getArtifactId())) {
                return true;
            }

            if (!transitive) {
                return true;
            }

            String scope = dependency.getScope();
            return !excluded.contains(scope);
        }

        @Override
        public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
            if (this.transitive || context.getDependency() == null) {
                return this;
            }

            return new ClientDepSelector(true, excluded);
        }
    }

    private static final class DependencyList {

        private final List<MavenDependency> mavenTransitiveDependencies;
        private final List<MavenDependency> mavenSingleDependencies;

        private final List<String> mavenExclusions;

        private final List<URI> directLinks;

        private DependencyList(List<MavenDependency> mavenTransitiveDependencies,
                               List<MavenDependency> mavenSingleDependencies,
                               List<String> mavenExclusions,
                               List<URI> directLinks) {

            this.mavenTransitiveDependencies = mavenTransitiveDependencies;
            this.mavenSingleDependencies = mavenSingleDependencies;
            this.mavenExclusions = mavenExclusions;
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
            if (listener == null) {
                return;
            }

            DependencyManagerException ex = exceptionConverter.convert(e);
            listener.onRetry(tryCount, retryCount, retryInterval, ex.getMessage());
        }

        public void transferFailed(TransferEvent event) {
            if (listener == null || event == null) {
                return;
            }

            String error = Optional.ofNullable(event.getException()).map(Throwable::toString).orElse("n/a");
            listener.onTransferFailed(event + ", error: " + error);
        }
    }

    private static <T> T withRetry(Callable<T> c,
                                   ResolveExceptionConverter exceptionConverter,
                                   ProgressNotifier notifier) throws IOException {
        try {
            return RetryUtils.withRetry(DependencyManager.RETRY_COUNT, DependencyManager.RETRY_INTERVAL,
                    c, ResolveRetryStrategy.INSTANCE, notifier);
        } catch (Exception e) {
            throw exceptionConverter.convert(e);
        }
    }

    private static class ResolveRetryStrategy implements RetryUtils.RetryStrategy {

        public static final ResolveRetryStrategy INSTANCE = new ResolveRetryStrategy();

        @Override
        public boolean canRetry(Exception e) {
            List<Throwable> exceptions = ExceptionUtils.getExceptionList(e);
            Throwable last = exceptions.get(exceptions.size() - 1);
            return !((last instanceof RepositoryOfflineException) || (last instanceof ArtifactNotFoundException));
        }
    }
}
