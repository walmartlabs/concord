package com.walmartlabs.concord.dependencymanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.*;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
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

public class DependencyManager {

    private static final Logger log = LoggerFactory.getLogger(DependencyManager.class);

    public static final String MAVEN_SCHEME = "mvn";
    public static final String CFG_FILE_KEY = "CONCORD_MAVEN_CFG";

    private static final String FILES_CACHE_DIR = "files";
    private static final String MAVEN_CACHE_DIR = "maven";

    private static final MavenRepository WALMART_WARM = new MavenRepository("warm", "default", "https://nexus.prod.walmart.com/nexus/content/groups/public/");
    private static final MavenRepository MAVEN_CENTRAL = new MavenRepository("central", "default", "https://repo.maven.apache.org/maven2/");
    private static final MavenRepository LOCAL_M2 = new MavenRepository("local", "default", "file://" + System.getProperty("user.home") + "/.m2/repository");
    private static final List<MavenRepository> DEFAULT_REPOS = Arrays.asList(LOCAL_M2, WALMART_WARM, MAVEN_CENTRAL);

    private final Path cacheDir;
    private final List<RemoteRepository> repositories;
    private final Object mutex = new Object();
    private final RepositorySystem maven = newMavenRepositorySystem();

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

        Collection<Path> result = new HashSet<>();
        for (URI item : items) {
            Collection<Path> deps = resolve(item);
            log.debug("collectDependencies ['{}'] -> resolved into: {}", item, deps);
            for (Path d : deps) {
                result.add(d.toAbsolutePath());
            }
        }
        return result;
    }

    public Collection<Path> resolve(URI item) throws IOException {
        String scheme = item.getScheme();
        if (MAVEN_SCHEME.equalsIgnoreCase(scheme)) {
            Map<String, String> cfg = splitQuery(item);
            String id = item.getAuthority();
            boolean transitive = Boolean.parseBoolean(cfg.getOrDefault("transitive", "true"));
            if (transitive) {
                boolean includeOptional = Boolean.parseBoolean(cfg.getOrDefault("includeOptional", "false"));
                String scope = cfg.getOrDefault("scope", JavaScopes.COMPILE);
                return resolveMavenTransitively(id, scope, includeOptional);
            } else {
                return Collections.singleton(resolveMavenSingle(id));
            }
        } else {
            return Collections.singleton(resolveFile(item));
        }
    }

    public Path resolveSingle(URI item) throws IOException {
        String scheme = item.getScheme();
        if (MAVEN_SCHEME.equalsIgnoreCase(scheme)) {
            String id = item.getAuthority();
            return resolveMavenSingle(id);
        } else {
            return resolveFile(item);
        }
    }

    public Path resolveFile(URI uri) throws IOException {
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

    private Path resolveMavenSingle(String id) throws IOException {
        RepositorySystemSession session = newRepositorySystemSession(maven);

        Artifact artifact = new DefaultArtifact(id);

        ArtifactRequest req = new ArtifactRequest();
        req.setArtifact(artifact);
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

    private Collection<Path> resolveMavenTransitively(String id, String scope, boolean includeOptional) throws IOException {
        RepositorySystem system = newMavenRepositorySystem();
        RepositorySystemSession session = newRepositorySystemSession(system);

        Artifact artifact = new DefaultArtifact(id);

        DependencyFilter filter = DependencyFilterUtils.classpathFilter(scope);

        if (!includeOptional) {
            filter = DependencyFilterUtils.andFilter(filter,
                    (node, parents) -> !node.getDependency().isOptional());
        }

        CollectRequest req = new CollectRequest();
        req.setRoot(new Dependency(artifact, scope));
        req.setRepositories(repositories);

        DependencyRequest dependencyRequest = new DependencyRequest(req, filter);

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
                log.info("artifactResolving -> {}", event);
            }

            @Override
            public void artifactResolved(RepositoryEvent event) {
                log.info("artifactResolved -> {}", event);
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
        throw new IllegalArgumentException("Invalid dependency URL: " + uri);
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
                .map(r -> new RemoteRepository.Builder(r.getId(), r.getContentType(), r.getUrl()).build())
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

            l.add(new MavenRepository(id, contentType, url));
        }
        return l;
    }
}
