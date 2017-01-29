package com.walmartlabs.concord.agent;

import com.google.common.base.Throwables;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named
@Singleton
public class ConfigurationProvider implements Provider<Configuration> {

    public static final String LOG_STORE_KEY = "agent.logStore";

    @Override
    public Configuration get() {
        try {
            Path logStore = getPath(LOG_STORE_KEY, "logStore");
            return new Configuration(logStore);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private static Path getPath(String key, String defaultPrefix) throws IOException {
        String s = System.getProperty(key);
        if (s == null) {
            return Files.createTempDirectory(defaultPrefix);
        }
        return Paths.get(s);
    }
}
