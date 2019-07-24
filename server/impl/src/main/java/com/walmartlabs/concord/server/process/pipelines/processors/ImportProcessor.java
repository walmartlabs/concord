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

import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.project.model.Import;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.server.cfg.ImportConfiguration;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.queueclient.message.ImportEntry;
import com.walmartlabs.concord.server.queueclient.message.Imports;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.project.model.Import.GitDefinition;
import static com.walmartlabs.concord.project.model.Import.MvnDefinition;
import static com.walmartlabs.concord.server.process.Payload.ORGANIZATION_ID;
import static com.walmartlabs.concord.server.queueclient.message.ImportEntry.GitEntry;
import static com.walmartlabs.concord.server.queueclient.message.ImportEntry.MvnEntry;

/**
 * Import external resources into the workspace.
 */
@Named
@Singleton
public class ImportProcessor implements PayloadProcessor {

    private static final String DEFAULT_VERSION = "master";
    private static final String DEFAULT_DEST = "concord";

    private final OrganizationDao orgDao;
    private final LogManager logManager;
    private final ImportManager importManager;
    private final ImportConfiguration cfg;

    @Inject
    public ImportProcessor(OrganizationDao orgDao, LogManager logManager, ImportManager importManager, ImportConfiguration cfg) {
        this.orgDao = orgDao;
        this.logManager = logManager;
        this.importManager = importManager;
        this.cfg = cfg;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        List<ImportEntry> imports = getImports(payload);
        if (imports.isEmpty()) {
            return chain.process(payload);
        }

        payload = payload.putHeader(Payload.IMPORTS, Imports.of(imports));

        Path workDir = payload.getHeader(Payload.WORKSPACE_DIR);
        try {
            List<Snapshot> snapshots = importManager.process(imports, workDir);
            for (Snapshot s : snapshots) {
                payload = addSnapshot(payload, s);
            }
        } catch (Exception e) {
            logManager.error(payload.getProcessKey(), "Error while importing external resource: {}", e.getMessage());
            throw new ProcessException(payload.getProcessKey(), "Error while importing external resource: " + e.getMessage(), e);
        }

        return chain.process(payload);
    }

    private List<ImportEntry> getImports(Payload payload) {
        ProjectDefinition pd = payload.getHeader(Payload.PROJECT_DEFINITION);
        if (pd == null) {
            return Collections.emptyList();
        }

        if (pd.getImports() != null && !pd.getImports().isEmpty()) {
            return convert(payload, pd.getImports());
        }

        return Collections.emptyList();
    }

    private static Payload addSnapshot(Payload payload, Snapshot s) {
        List<Snapshot> result = new ArrayList<>();
        List<Snapshot> snapshots = payload.getHeader(RepositoryProcessor.REPOSITORY_SNAPSHOT);
        if (snapshots != null) {
            result.addAll(snapshots);
        }
        result.add(s);
        return payload.putHeader(RepositoryProcessor.REPOSITORY_SNAPSHOT, result);
    }

    private List<ImportEntry> convert(Payload payload, List<Import> items) {
        if (items == null) {
            return Collections.emptyList();
        }

        List<ImportEntry> result = new ArrayList<>();
        for (Import i : items) {
            result.add(convert(payload, i));
        }
        return result;
    }

    private ImportEntry convert(Payload payload, Import i) {
        switch (i.type()) {
            case "git": {
                GitDefinition e = (GitDefinition) i;
                return convertGitImport(payload, e);
            }
            case "mvn": {
                MvnDefinition e = (MvnDefinition) i;
                return MvnEntry.builder()
                        .url(e.url())
                        .dest(e.dest() != null ? e.dest() : DEFAULT_DEST)
                        .build();
            }
            default: {
                throw new IllegalArgumentException("Unknown import type: '" + i.type() + "'");
            }
        }
    }

    private ImportEntry convertGitImport(Payload payload, GitDefinition e) {
        String url = e.url();
        if (url == null) {
            String name = e.name();
            url = normalizeUrl(cfg.getSrc()) + name;
        }

        Import.SecretDefinition secret = e.secret();
        if (secret != null && secret.org() == null) {
            String secretOrgName = getOrgName(payload);
            if (secretOrgName == null) {
                logManager.error(payload.getProcessKey(), "Error while importing external resource - can't determine the '{}' secret's organization", secret.name());
                throw new ProcessException(payload.getProcessKey(), "Can't determine organization for secret: '" + secret.name() + "'");
            }

            secret = Import.SecretDefinition.builder().from(secret)
                    .org(secretOrgName)
                    .build();
        }

        return GitEntry.builder()
                .url(url)
                .version(e.version() != null ? e.version() : DEFAULT_VERSION)
                .path(e.path())
                .dest(e.dest() != null ? e.dest() : DEFAULT_DEST)
                .secret(secret)
                .build();
    }

    private String getOrgName(Payload payload) {
        UUID orgId = payload.getHeader(ORGANIZATION_ID);
        if (orgId == null) {
            return null;
        }

        OrganizationEntry org = orgDao.get(orgId);
        if (org == null) {
            return null;
        }
        return org.getName();
    }

    private static String normalizeUrl(String u) {
        if (u.endsWith("/")) {
            return u;
        }
        return u + "/";
    }
}
