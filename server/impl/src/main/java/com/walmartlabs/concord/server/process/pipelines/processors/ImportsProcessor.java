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

import com.walmartlabs.concord.common.SensitiveData;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.repository.Repository;
import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.server.cfg.ImportsConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.repository.RepositoryManager;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.sdk.MapUtils.getString;

/**
 * Extracts templates/imports into the workspace.
 */
@Named
@Singleton
public class ImportsProcessor implements PayloadProcessor {

    private static final Secret INLINE_URL_SECRET = new Secret() {};

    private final LogManager logManager;
    private final RepositoryManager repositoryManager;
    private final ImportsConfiguration cfg;

    @Inject
    public ImportsProcessor(LogManager logManager, RepositoryManager repositoryManager, ImportsConfiguration cfg) {
        this.logManager = logManager;
        this.repositoryManager = repositoryManager;
        this.cfg = cfg;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();
        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);

        Object imports = req.get(InternalConstants.Request.IMPORTS_KEY);
        if (!(imports instanceof List)) {
            return chain.process(payload);
        }

        List<ImportItem> importItems = convert(processKey, (List<Object>) imports);
        Path workDir = payload.getHeader(Payload.WORKSPACE_DIR);

        for (ImportItem t : importItems) {
            try {
                Snapshot s = SensitiveData.withSensitiveData(getSensitiveData(t), () -> processImportItem(t, workDir));
                logManager.info(processKey, "Import {} processed", t);
            } catch (Exception e) {
                logManager.error(processKey, "Process import {} error: {}", t, e.getMessage());
                throw new ProcessException(processKey, "Process import " + t + " error", e);
            }
        }

        return chain.process(payload);
    }

    private Snapshot processImportItem(ImportItem item, Path workDir) {
        return repositoryManager.withLock(item.url(), () -> {
            Repository repository = repositoryManager.fetch(item.url(), item.version(), null, item.path(), INLINE_URL_SECRET);
            Path dst = workDir;
            if (item.dest() != null) {
                dst = dst.resolve(item.dest());
            }
            return repository.export(dst);
        });
    }

    @SuppressWarnings("unchecked")
    private List<ImportItem> convert(ProcessKey processKey, List<Object> importItems) {
        List<ImportItem> result = new ArrayList<>();
        for (Object o : importItems) {
            if (!(o instanceof Map)) {
                throw new ProcessException(processKey, "Invalid imports definition type, expected map, got: " + o.getClass());
            }

            Map<String, Object> importParams = (Map<String, Object>) o;
            String url = getString(importParams, "url");
            if (url == null) {
                String name = getString(importParams, "name");
                if (name == null) {
                    throw new ProcessException(processKey, "Invalid imports definition: expected name or url");
                }
                url = normalizeUrl(cfg.getSrc()) + name;
            }

            result.add(ImportItem.builder()
                    .url(url)
                    .version(getString(importParams, "version", "master"))
                    .path(getString(importParams, "path"))
                    .dest(getString(importParams, "dest", "concord"))
                    .build());
        }
        return result;
    }

    private static String normalizeUrl(String u) {
        if (u.endsWith("/")) {
            return u;
        }
        return u + "/";
    }

    private static List<String> getSensitiveData(ImportItem item) throws URISyntaxException {
        URI uri = new URI(item.url());
        String userInfo = uri.getUserInfo();
        if (userInfo == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(userInfo);
    }

    @Value.Immutable
    static abstract class ImportItem {

        @Value.Redacted
        public abstract String url();

        public abstract String version();

        @Nullable
        public abstract String path();

        public abstract String dest();

        static ImmutableImportItem.Builder builder() {
            return ImmutableImportItem.builder();
        }

        public String toString() {
            String url;
            try {
                url = SensitiveData.hide(getSensitiveData(this), url());
            } catch (Exception e) {
                url = url();
            }

            return '{' + "URL=" + url +
                    ", version=" + version() +
                    ", path=" + path() +
                    ", dest=" + dest() +
                    '}';
        }
    }
}
