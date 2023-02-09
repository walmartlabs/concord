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

    @InjectVariable("workDir")
    private String workDir;

    public String asString(String path) throws IOException {
        return delegate(null).asString(path);
    }

    public Object asJson(String path) throws IOException {
        return asJson(null, path, false);
    }

    public Object asJson(@InjectVariable("context") Context ctx, String path, boolean eval) throws IOException {
        return delegate(ctx).asJson(path, eval);
    }

    public Object fromJsonString(String jsonString) throws IOException {
        return fromJsonString(null, jsonString, false);
    }

    public Object fromJsonString(@InjectVariable("context") Context ctx,
                                 String jsonString, boolean eval) throws IOException {
        return delegate(ctx).fromJsonString(jsonString, eval);
    }

    public Object asYaml(String path) throws IOException {
        return asYaml(null, path, false);
    }

    public Object asYaml(@InjectVariable("context") Context ctx, String path, boolean eval) throws IOException {
        return delegate(ctx).asYaml(path, eval);
    }

    public String writeAsJson(Object content, @InjectVariable("workDir") String workDir) throws IOException {
        return delegate(null).writeAsJson(content);
    }

    public String writeAsString(String content, @InjectVariable("workDir") String workDir) throws IOException {
        return delegate(null).writeAsString(content);
    }

    public String writeAsYaml(Object content, @InjectVariable("workDir") String workDir) throws IOException {
        return delegate(null).writeAsYaml(content);
    }

    public String printJson(Object value) throws IOException {
        return ResourceTaskCommon.printJson(value);
    }

    public String prettyPrintJson(Object value) throws IOException {
        return ResourceTaskCommon.prettyPrintJson(value);
    }

    public String prettyPrintYaml(Object value) throws IOException {
        return ResourceTaskCommon.prettyPrintYaml(value);
    }

    public String prettyPrintYaml(Object value, int indent) throws IOException {
        return ResourceTaskCommon.prettyPrintYaml(value, indent);
    }

    private ResourceTaskCommon delegate(Context ctx) {
        Evaluator evaluator = null;
        if (ctx != null) {
            evaluator = ctx::interpolate;
        }
        Path wd = Paths.get(workDir);
        return new ResourceTaskCommon(wd,
                (prefix, suffix) -> createTempFile(wd, prefix, suffix), evaluator);
    }

    private static Path createTempFile(Path baseDir, String prefix, String suffix) throws IOException {
        Path tempDir = assertTempDir(baseDir);
        return Files.createTempFile(tempDir, prefix, suffix);
    }

    private static Path assertTempDir(Path baseDir) throws IOException {
        Path p = baseDir.resolve(Constants.Files.CONCORD_TMP_DIR_NAME);
        if (!p.toFile().exists()) {
            Files.createDirectories(p);
        }

        return p;
    }
}
