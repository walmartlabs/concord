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
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.cfg.TemplateConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.template.TemplateAliasDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Named
@Singleton
/**
 * Extracts template files into the workspace.
 */
public class TemplateFilesProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(TemplateFilesProcessor.class);

    private final LogManager logManager;
    private final DependencyManager dependencyManager;
    private final TemplateAliasDao aliasDao;

    @Inject
    public TemplateFilesProcessor(TemplateConfiguration cfg,
                                  LogManager logManager,
                                  TemplateAliasDao aliasDao) throws IOException {

        this.logManager = logManager;
        this.aliasDao = aliasDao;
        this.dependencyManager = new DependencyManager(cfg.getCacheDir());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();
        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);

        String s = (String) req.get(InternalConstants.Request.TEMPLATE_KEY);
        if (s == null) {
            return chain.process(payload);
        }

        try {
            URI uri = getUri(instanceId, s);
            Path template = dependencyManager.resolveSingle(uri).getPath();
            payload = process(payload, template);

            return chain.process(payload);
        } catch (URISyntaxException | IOException e) {
            logManager.error(instanceId, "Template error: " + s, e);
            throw new ProcessException(instanceId, "Error while processing a template: " + s, e);
        }
    }

    private URI getUri(UUID instanceId, String template) throws URISyntaxException {
        try {
            URI u = new URI(template);

            String scheme = u.getScheme();
            // doesn't look like a URI, let's try find an alias
            if (scheme == null || scheme.trim().isEmpty()) {
                return getByAlias(instanceId, template);
            }

            return u;
        } catch (URISyntaxException e) {
            return getByAlias(instanceId, template);
        }
    }

    private URI getByAlias(UUID instanceId, String s) throws URISyntaxException {
        Optional<String> o = aliasDao.get(s);
        if (!o.isPresent()) {
            throw new ProcessException(instanceId, "Invalid template URL or alias: " + s);
        }

        return new URI(o.get());
    }

    private Payload process(Payload payload, Path template) throws IOException {
        UUID instanceId = payload.getInstanceId();
        Path workspacePath = payload.getHeader(Payload.WORKSPACE_DIR);

        // copy template's files to the payload, skipping the existing files
        IOUtils.unzip(template, workspacePath, true);

        log.debug("process ['{}', '{}'] -> done", instanceId, template);
        return payload;
    }
}
