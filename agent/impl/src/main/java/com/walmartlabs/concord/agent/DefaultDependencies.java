package com.walmartlabs.concord.agent;

import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultDependencies {

    private static final Logger log = LoggerFactory.getLogger(DefaultDependencies.class);

    public static final String CFG_KEY = "DEFAULT_DEPS_CFG";

    private final List<URI> dependencies;

    public DefaultDependencies() {
        String path = System.getenv(CFG_KEY);
        if (path != null) {
            try (Stream<String> stream = Files.lines(Paths.get(path))) {
                this.dependencies = stream.map(DefaultDependencies::parseUri)
                .collect(Collectors.toList());
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }

            log.info("init -> using external default dependencies configuration: {}", path);
        } else {
            try (InputStream is = DefaultDependencies.class.getResourceAsStream("default-dependencies")) {
                this.dependencies = new BufferedReader(new InputStreamReader(is)).lines()
                        .map(DefaultDependencies::parseUri)
                        .collect(Collectors.toList());
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }

            log.info("init -> using classpath default dependencies configuration");
        }
    }

    public List<URI> getDependencies() {
        return dependencies;
    }

    private static URI parseUri(String s) {
        try {
            return new URI(s);
        } catch (URISyntaxException e) {
            throw Throwables.propagate(e);
        }
    }
}
