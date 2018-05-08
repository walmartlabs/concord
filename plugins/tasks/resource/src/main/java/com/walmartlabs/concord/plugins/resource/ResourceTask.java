package com.walmartlabs.concord.plugins.resource;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named("resource")
@SuppressWarnings("unused")
public class ResourceTask implements Task {

    @SuppressWarnings("unused")
    public String asString(String path) throws IOException {
        byte[] ab = Files.readAllBytes(Paths.get(path));
        return new String(ab);
    }

    @SuppressWarnings("unused")
    public Object asJson(String path) throws IOException {
        byte[] ab = Files.readAllBytes(Paths.get(path));
        return new ObjectMapper().readValue(ab, Object.class);
    }

    @SuppressWarnings("unused")
    public String writeAsJson(Object content, @InjectVariable("workDir") String workDir) throws IOException {
        Path baseDir = Paths.get(workDir);
        Path tempDir = assertTempDir(baseDir);

        Path resourceFile = Files.createTempFile(tempDir, "resource_", ".json");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(resourceFile.toFile(), content);

        return baseDir.relativize(resourceFile.toAbsolutePath()).toString();
    }

    @SuppressWarnings("unused")
    public String writeAsString(String content, @InjectVariable("workDir") String workDir) throws IOException {
        Path baseDir = Paths.get(workDir);
        Path tempDir = assertTempDir(baseDir);

        Path resourceFile = Files.createTempFile(tempDir, "resource_", ".txt");
        Files.write(resourceFile, content.getBytes());

        return baseDir.relativize(resourceFile.toAbsolutePath()).toString();
    }

    private Path assertTempDir(Path baseDir) throws IOException {
        Path p = baseDir.resolve(".tmp");
        if (!p.toFile().exists()) {
            Files.createDirectories(p);
        }

        return p;
    }
}
