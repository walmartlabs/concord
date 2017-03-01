package com.walmartlabs.concord.server.template;

import com.google.common.collect.Sets;
import com.jcabi.aether.Aether;
import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.cfg.TemplateConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.JavaScopes;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipInputStream;

@Named
public class TemplateResolver {

    private static final Logger log = LoggerFactory.getLogger(TemplateResolver.class);

    // TODO externalize
    private static final Set<String> BLACKLISTED_ARTIFACTS = Sets.newHashSet(
            "bpm-engine-api",
            "bpm-engine-impl",
            "jackson-databind",
            "jackson-annotations",
            "jackson-core",
            "concord-common",
            "javax.inject",
            "slf4j-api"
    );

    private final TemplateConfiguration cfg;
    private final TemplateDao templateDao;

    @Inject
    public TemplateResolver(TemplateConfiguration cfg, TemplateDao templateDao) {
        this.cfg = cfg;
        this.templateDao = templateDao;
    }

    public Path get(String name) throws IOException {
        // check the predefined templates first
        Path dst = resolvePredefined(name);

        // check the db, if there is no predefined templates
        // with the specified name
        if (dst == null) {
            dst = resolveDb(name);
        }

        // no templates found, nothing to do
        if (dst == null) {
            return null;
        }

        // resolve and add the required dependencies
        resolveDependencies(dst);

        return dst;
    }

    public boolean exists(String name) {
        return getPredefinedTemplateUrl(name) != null || templateDao.exists(name);
    }

    private Path resolvePredefined(String name) throws IOException {
        URL url = getPredefinedTemplateUrl(name);
        if (url == null) {
            return null;
        }

        try {
            URI uri = url.toURI();
            return makeCopy(Paths.get(uri));
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    private Path resolveDb(String name) throws IOException {
        try (InputStream in = templateDao.getData(name)) {
            if (in == null) {
                return null;
            }

            try (ZipInputStream zip = new ZipInputStream(in)) {
                Path dst = Files.createTempDirectory("template");
                IOUtils.unzip(zip, dst);
                return dst;
            }
        }
    }

    private void resolveDependencies(Path src) throws IOException {
        Path deps = src.resolve(Constants.DEPENDENCIES_FILE_NAME);
        if (!Files.exists(deps)) {
            return;
        }

        List<String> gavs = Files.readAllLines(deps);
        if (gavs.isEmpty()) {
            return;
        }

        File local = cfg.getDepsDir().toFile();
        Aether aether = new Aether(Collections.emptyList(), local);

        Path dstDir = src.resolve(Constants.LIBRARIES_DIR_NAME);
        if (!Files.exists(dstDir)) {
            Files.createDirectories(dstDir);
        }

        for (String gav : gavs) {
            try {
                Collection<Artifact> as = aether.resolve(new DefaultArtifact(gav), JavaScopes.RUNTIME);
                if (as.isEmpty()) {
                    throw new IOException("Can't resolve dependencies: " + gav);
                }

                for (Artifact a : as) {
                    if (!valid(a)) {
                        log.debug("resolveDependencies -> skipping '{}'", a);
                        continue;
                    }

                    Path dst = dstDir.resolve(a.getFile().getName());
                    Files.copy(a.getFile().toPath(), dst);
                    log.info("resolveDependencies -> added {}", a.getFile(), dst);
                }
            } catch (DependencyResolutionException e) {
                throw new IOException(e);
            }
        }
    }

    private Path makeCopy(Path src) throws IOException {
        Path dst = Files.createTempDirectory("template");
        IOUtils.copy(src, dst);
        return dst;
    }

    private static boolean valid(Artifact a) {
        String aId = a.getArtifactId();
        return !BLACKLISTED_ARTIFACTS.contains(aId);
    }

    private static URL getPredefinedTemplateUrl(String name) {
        return TemplateResolver.class.getResource(name);
    }
}
