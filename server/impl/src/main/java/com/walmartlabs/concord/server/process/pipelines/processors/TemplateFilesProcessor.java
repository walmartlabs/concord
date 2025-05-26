package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.template.TemplateAliasDao;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Extracts template files into the workspace.
 */
public class TemplateFilesProcessor implements PayloadProcessor {

    private final ProcessLogManager logManager;
    private final DependencyManager dependencyManager;
    private final TemplateAliasDao aliasDao;

    @Inject
    public TemplateFilesProcessor(DependencyManager dependencyManager,
                                  ProcessLogManager logManager,
                                  TemplateAliasDao aliasDao) {

        this.logManager = logManager;
        this.aliasDao = aliasDao;
        this.dependencyManager = dependencyManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();
        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);

        Object s = cfg.get(Constants.Request.TEMPLATE_KEY);
        if (!(s instanceof String)) {
            return chain.process(payload);
        }

        try {
            URI uri = getUri(processKey, (String) s);
            Path template = dependencyManager.resolveSingle(uri).getPath();
            extract(payload, template);

            return chain.process(payload);
        } catch (URISyntaxException | IOException e) {
            logManager.error(processKey, "Template error: " + s, e);
            throw new ProcessException(processKey, "Error while processing a template: " + s, e);
        }
    }

    private URI getUri(PartialProcessKey processKey, String template) throws URISyntaxException {
        try {
            URI u = new URI(template);

            String scheme = u.getScheme();
            // doesn't look like a URI, let's try find an alias
            if (scheme == null || scheme.trim().isEmpty()) {
                return getByAlias(processKey, template);
            }

            return u;
        } catch (URISyntaxException e) {
            return getByAlias(processKey, template);
        }
    }

    private URI getByAlias(PartialProcessKey processKey, String s) throws URISyntaxException {
        Optional<String> o = aliasDao.get(s);
        if (!o.isPresent()) {
            throw new ProcessException(processKey, "Invalid template URL or alias: " + s);
        }

        return new URI(o.get());
    }

    private void extract(Payload payload, Path template) throws IOException {
        Path workspacePath = payload.getHeader(Payload.WORKSPACE_DIR);

        // copy template's files to the payload, skipping the existing files
        IOUtils.unzip(template, workspacePath, true);
    }
}
