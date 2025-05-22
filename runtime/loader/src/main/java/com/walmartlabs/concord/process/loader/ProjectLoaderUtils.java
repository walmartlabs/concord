package com.walmartlabs.concord.process.loader;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ProjectLoaderUtils {

    public static Optional<String> getRuntimeType(Path workDir) throws IOException {
        for (var filename : Constants.Files.PROJECT_ROOT_FILE_NAMES) {
            var src = workDir.resolve(filename);
            if (Files.exists(src)) {
                var mapper = new YAMLMapper();
                try (var in = Files.newInputStream(src)) {
                    var n = mapper.readTree(in);

                    n = n.get(Constants.Request.CONFIGURATION_KEY);
                    if (n == null) {
                        continue;
                    }

                    n = n.get(Constants.Request.RUNTIME_KEY);
                    if (n == null) {
                        continue;
                    }

                    var s = n.textValue();
                    if (s != null) {
                        return Optional.of(s);
                    }
                }
            }
        }

        return Optional.empty();
    }

    public static boolean isConcordFileExists(Path repoPath) {
        for (String projectFileName : Constants.Files.PROJECT_ROOT_FILE_NAMES) {
            Path projectFile = repoPath.resolve(projectFileName);
            if (Files.exists(projectFile)) {
                return true;
            }
        }

        return false;
    }

    private ProjectLoaderUtils() {
    }
}
