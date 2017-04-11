package com.walmartlabs.concord.server.process.pipelines.processors;

import com.google.common.collect.Sets;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.cfg.DependencyStoreConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Named
public class DependenciesProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(DependenciesProcessor.class);
    private static final String SYSTEM_PREFIX = "concord://";

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
    private final Map<String, String> substituteData;

    @Inject
    public DependenciesProcessor(DependencyStoreConfiguration cfg) {
        this.cfg = cfg;

        Properties props = new Properties();
        try {
            props.load(DependenciesProcessor.class.getResourceAsStream("dependencies.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.substituteData = props.entrySet().stream()
                .collect(Collectors.toMap(e -> (String) e.getKey(), e -> (String) e.getValue()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> request = payload.getHeader(Payload.REQUEST_DATA_MAP);

        // get a list of dependencies from the request data
        Collection<String> deps = deps(request);
        if (deps == null) {
            return chain.process(payload);
        }

        // filter all valid dependencies
        Collection<String> validDeps = deps.stream()
                .filter(e -> valid(path(e)))
                .collect(Collectors.toSet());

        // collect "system" dependencies
        Collection<String> systemDeps = validDeps.stream()
                .filter(DependenciesProcessor::system)
                .collect(Collectors.toSet());

        // copy "system" dependencies into the workspace's lib directory
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        try {
            processSystemDependencies(payload.getInstanceId(), workspace, cfg.getDepsDir(), systemDeps);
        } catch (IOException e) {
            log.error("process ['{}'] -> error while processing system dependencies", payload.getInstanceId(), e);
            throw new ProcessException("Error while processing system dependencies", e);
        }

        // the rest of the dependencies will be resolved by an agent
        Collection<String> rest = new HashSet<>(validDeps);
        rest.removeAll(systemDeps);

        request.put(Constants.Request.DEPENDENCIES_KEY, rest);
        payload = payload.putHeader(Payload.REQUEST_DATA_MAP, request);

        return chain.process(payload);
    }

    private void processSystemDependencies(String instanceId, Path workspace, Path depsDir,
                                           Collection<String> deps) throws IOException {

        if (deps.isEmpty()) {
            return;
        }

        if (depsDir == null) {
            log.warn("processSystemDependencies ['{}'] -> dependencies directory not set, skipping", instanceId);
            return;
        }

        Path libDir = workspace.resolve(Constants.Files.LIBRARIES_DIR_NAME);
        if (!Files.exists(libDir)) {
            Files.createDirectories(libDir);
        }

        for (String d : deps) {
            String s = substitute(path(d));

            Path src = depsDir.resolve(s);
            if (!Files.exists(src)) {
                throw new ProcessException("Dependency not found: " + d);
            }

            Path dst = libDir.resolve(s);
            IOUtils.copy(src, dst);
        }
    }

    private static String path(String a) {
        URI uri = URI.create(a);
        String s = uri.getPath();

        if (s == null || s.trim().isEmpty()) {
            throw new ProcessException("Invalid dependency URI path: " + a);
        }

        if (s.startsWith("/")) {
            s = s.substring(1);
        }

        return s;
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> deps(Map<String, Object> req) {
        Object o = req.get(Constants.Request.DEPENDENCIES_KEY);
        if (o == null) {
            return null;
        }

        if (o instanceof Collection) {
            return (Collection<String>) o;
        }

        if (o instanceof ScriptObjectMirror) {
            ScriptObjectMirror m = (ScriptObjectMirror) o;
            if (!m.isArray()) {
                throw new ProcessException("Invalid dependencies object type. Expected a JavaScript array, got: " + m);
            }

            String[] as = m.to(String[].class);
            return Arrays.asList(as);
        }

        throw new ProcessException("Invalid dependencies object type. Expected an array or a collection, got: " + o.getClass());
    }

    private String substitute(String s) {
        StrSubstitutor subs = new StrSubstitutor(substituteData);
        return subs.replace(s);
    }

    private static boolean valid(String a) {
        return BLACKLISTED_ARTIFACTS.stream().noneMatch(a::matches);
    }

    private static boolean system(String a) {
        return a.startsWith(SYSTEM_PREFIX);
    }
}
