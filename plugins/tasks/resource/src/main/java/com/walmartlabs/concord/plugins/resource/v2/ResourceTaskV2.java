package com.walmartlabs.concord.plugins.resource.v2;

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

import com.walmartlabs.concord.plugins.resource.ResourceTaskCommon;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.DryRunReady;
import com.walmartlabs.concord.runtime.v2.sdk.Task;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.Map;

@Named("resource")
@DryRunReady
@SuppressWarnings("unused")
public class ResourceTaskV2 implements Task {

    private final ResourceTaskCommon delegate;

    @Inject
    public ResourceTaskV2(Context ctx) {
        this.delegate = new ResourceTaskCommon(ctx.workingDirectory(), ctx.fileService()::createTempFile, v -> ctx.eval(v, Object.class));
    }

    public String asString(String path) throws IOException {
        return delegate.asString(path);
    }

    public Object asJson(String path) throws IOException {
        return delegate.asJson(path);
    }

    public Object asJson(String path, boolean eval) throws IOException {
        return delegate.asJson(path, eval);
    }

    public Map<String, Object> asProperties(String path) throws IOException {
        return delegate.asProperties(path);
    }

    public Map<String, Object> asProperties(String path, boolean eval) throws IOException {
        return delegate.asProperties(path, eval);
    }

    public Object fromJsonString(String jsonString) throws IOException {
        return fromJsonString(jsonString, false);
    }

    public Object fromJsonString(String jsonString, boolean eval) throws IOException {
        return delegate.fromJsonString(jsonString, eval);
    }

    public Object fromYamlString(String yamlString) throws IOException {
        return fromYamlString(yamlString, false);
    }

    public Object fromYamlString(String yamlString, boolean eval) throws IOException {
        return delegate.fromJsonString(yamlString, eval);
    }

    public Object asYaml(String path) throws IOException {
        return delegate.asYaml(path);
    }

    public Object asYaml(String path, boolean eval) throws IOException {
        return delegate.asYaml(path, eval);
    }

    public String writeAsJson(Object content) throws IOException {
        return delegate.writeAsJson(content);
    }

    public String writeAsJson(Object content, String path) throws IOException {
        return delegate.writeAsJson(content, path);
    }

    public String writeAsString(String content) throws IOException {
        return delegate.writeAsString(content);
    }

    public String writeAsString(String content, String path) throws IOException {
        return delegate.writeAsString(content, path);
    }

    public String writeAsYaml(Object content) throws IOException {
        return delegate.writeAsYaml(content);
    }

    public String writeAsYaml(Object content, String path) throws IOException {
        return delegate.writeAsYaml(content, path);
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
}
