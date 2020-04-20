package com.walmartlabs.concord.plugins.resource;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.FileService;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Path;

@Named("resource")
@SuppressWarnings("unused")
public class ResourceTaskV2 implements Task {

    private final FileService fileService;
    private final WorkingDirectory workDir;

    @Inject
    public ResourceTaskV2(FileService fileService, WorkingDirectory workDir) {
        this.fileService = fileService;
        this.workDir = workDir;
    }

    public String asString(String path) throws IOException {
        return ResourceUtils.asString(path);
    }

    public Object asJson(String path) throws IOException {
        return asJson(null, path, false);
    }

    public Object asJson(TaskContext ctx, String path, boolean eval) throws IOException {
        Object result = ResourceUtils.asJson(path);
        return eval ? ctx.eval(result, result.getClass()) : result;
    }

    public Object asYaml(String path) throws IOException {
        return asYaml(null, path, false);
    }

    public Object asYaml(TaskContext ctx, String path, boolean eval) throws IOException {
        Object result = ResourceUtils.asYaml(path);
        return eval ? ctx.eval(result, result.getClass()) : result;
    }

    public String writeAsJson(Object content) throws IOException {
        Path tempFile = fileService.createTempFile(ResourceUtils.RESOURCE_PREFIX, ResourceUtils.JSON_FILE_SUFFIX);
        ResourceUtils.writeAsJson(content, tempFile);
        return workDir.getValue().relativize(tempFile.toAbsolutePath()).toString();
    }

    public String writeAsString(String content) throws IOException {
        Path tempFile = fileService.createTempFile(ResourceUtils.RESOURCE_PREFIX, ResourceUtils.TEXT_FILE_SUFFIX);
        ResourceUtils.writeAsString(content, tempFile);
        return workDir.getValue().relativize(tempFile.toAbsolutePath()).toString();
    }

    public String writeAsYaml(Object content) throws IOException {
        Path tempFile = fileService.createTempFile(ResourceUtils.RESOURCE_PREFIX, ResourceUtils.YAML_FILE_SUFFIX);
        ResourceUtils.writeAsYaml(content, tempFile);
        return workDir.getValue().relativize(tempFile.toAbsolutePath()).toString();
    }

    public String prettyPrintJson(Object json) throws IOException {
        return ResourceUtils.prettyPrintJson(json);
    }
}
