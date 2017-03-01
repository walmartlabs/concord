package com.walmartlabs.concord.server.process.pipelines.processors;

import com.google.common.collect.Sets;
import com.jcabi.aether.Aether;
import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.server.cfg.DependencyStoreConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Named
public class DependenciesProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(DependenciesProcessor.class);

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

    private final DependencyStoreConfiguration cfg;

    @Inject
    public DependenciesProcessor(DependencyStoreConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public Payload process(Payload payload) {
        Path src = payload.getHeader(Payload.WORKSPACE_DIR);

        try {
            resolveDependencies(src);
        } catch (IOException e) {
            throw new ProcessException("Error while processing payload's dependencies", e);
        }

        return payload;
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

    private static boolean valid(Artifact a) {
        String aId = a.getArtifactId();
        return !BLACKLISTED_ARTIFACTS.contains(aId);
    }
}
