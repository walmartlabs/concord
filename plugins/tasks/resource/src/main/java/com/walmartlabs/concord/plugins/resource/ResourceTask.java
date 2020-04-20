package com.walmartlabs.concord.plugins.resource;

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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
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

    public String asString(String path) throws IOException {
       return ResourceUtils.asString(path);
    }

    public Object asJson(String path) throws IOException {
        return asJson(null, path, false);
    }

    public Object asJson(@InjectVariable("context") Context ctx, String path, boolean eval) throws IOException {
        Object result = ResourceUtils.asJson(path);
        return eval ? ctx.interpolate(result) : result;
    }

    public Object asYaml(String path) throws IOException {
        return asYaml(null, path, false);
    }

    public Object asYaml(@InjectVariable("context") Context ctx, String path, boolean eval) throws IOException {
        Object result = ResourceUtils.asYaml(path);
        return eval ? ctx.interpolate(result) : result;
    }

    public String writeAsJson(Object content, @InjectVariable("workDir") String workDir) throws IOException {
        Path baseDir = Paths.get(workDir);
        Path tempFile = createTempFile(baseDir, ResourceUtils.RESOURCE_PREFIX, ResourceUtils.JSON_FILE_SUFFIX);
        ResourceUtils.writeAsJson(content, tempFile);
        return baseDir.relativize(tempFile.toAbsolutePath()).toString();
    }

    public String writeAsString(String content, @InjectVariable("workDir") String workDir) throws IOException {
        Path baseDir = Paths.get(workDir);
        Path tempFile = createTempFile(baseDir, ResourceUtils.RESOURCE_PREFIX, ResourceUtils.TEXT_FILE_SUFFIX);
        ResourceUtils.writeAsString(content, tempFile);
        return baseDir.relativize(tempFile.toAbsolutePath()).toString();
    }

    public String writeAsYaml(Object content, @InjectVariable("workDir") String workDir) throws IOException {
        Path baseDir = Paths.get(workDir);
        Path tempFile = createTempFile(baseDir, ResourceUtils.RESOURCE_PREFIX, ResourceUtils.YAML_FILE_SUFFIX);
        ResourceUtils.writeAsYaml(content, tempFile);
        return baseDir.relativize(tempFile.toAbsolutePath()).toString();
    }

    public String prettyPrintJson(Object json) throws IOException {
        return ResourceUtils.prettyPrintJson(json);
    }

    private Path createTempFile(Path baseDir, String prefix, String suffix) throws IOException {
        Path tempDir = assertTempDir(baseDir);
        return Files.createTempFile(tempDir, prefix, suffix);
    }

    private Path assertTempDir(Path baseDir) throws IOException {
        Path p = baseDir.resolve(Constants.Files.CONCORD_TMP_DIR_NAME);
        if (!p.toFile().exists()) {
            Files.createDirectories(p);
        }

        return p;
    }
}
