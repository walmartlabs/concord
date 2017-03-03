package com.walmartlabs.concord.server.process.pipelines.processors;

import com.google.common.collect.Sets;
import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.server.cfg.DependencyStoreConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@Named
public class DependenciesProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(DependenciesProcessor.class);

    // TODO externalize
    private static final Set<String> BLACKLISTED_ARTIFACTS = Sets.newHashSet(
            "bpm-engine-api.*",
            "bpm-engine-impl.*",
            "jackson-databind.*",
            "jackson-annotations.*",
            "jackson-core.*",
            "concord-common.*",
            "javax.inject.*",
            "slf4j-api.*"
    );

    private final DependencyStoreConfiguration cfg;

    @Inject
    public DependenciesProcessor(DependencyStoreConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public Payload process(Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);

        try {
            resolveDependencies(workspace);
        } catch (IOException e) {
            throw new ProcessException("Error while processing payload's dependencies", e);
        }

        return payload;
    }

    private void resolveDependencies(Path workspace) throws IOException {
        Path deps = workspace.resolve(Constants.DEPENDENCIES_FILE_NAME);
        if (!Files.exists(deps)) {
            return;
        }

        List<String> artifacts = Files.readAllLines(deps);
        if (artifacts.isEmpty()) {
            return;
        }

        Path srcDir = cfg.getDepsDir();

        Path dstDir = workspace.resolve(Constants.LIBRARIES_DIR_NAME);
        if (!Files.exists(dstDir)) {
            Files.createDirectories(dstDir);
        }

        for (String a : artifacts) {
            if (!valid(a)) {
                log.warn("resolveDependencies -> skipping '{}'", a);
                continue;
            }

            Path src = srcDir.resolve(a);
            if (!Files.exists(src)) {
                throw new IOException("File not found: " + src);
            }

            Path dst = dstDir.resolve(a);
            Files.copy(src, dst);
        }
    }

    private static boolean valid(String a) {
        return !BLACKLISTED_ARTIFACTS.stream().anyMatch(p -> a.matches(p));
    }
}
