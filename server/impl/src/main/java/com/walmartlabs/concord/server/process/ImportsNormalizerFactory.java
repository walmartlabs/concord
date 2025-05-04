package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.imports.Import;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.process.loader.ImportsNormalizer;
import com.walmartlabs.concord.server.cfg.ImportConfiguration;
import com.walmartlabs.concord.server.org.project.ProjectDao;

import javax.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Creates a {@link ImportsNormalizer} instance to fill the necessary fields
 * in the process' {@code imports} based on the project.
 */
public class ImportsNormalizerFactory {

    private static final String DEFAULT_DEST = "concord";

    private final ImportConfiguration cfg;
    private final ProjectDao projectDao;

    @Inject
    public ImportsNormalizerFactory(ImportConfiguration cfg, ProjectDao projectDao) {
        this.cfg = cfg;
        this.projectDao = projectDao;
    }

    public ImportsNormalizer forProject(UUID projectId) {
        return imports -> ImportsNormalizerFactory.this.normalize(ImportContext.ofProject(projectId), imports);
    }

    private Imports normalize(ImportContext ctx, Imports imports) {
        List<Import> items = imports != null ? imports.items() : null;
        if (items == null || items.isEmpty()) {
            return Imports.builder().build();
        }

        return Imports.of(items.stream()
                .map(i -> normalize(ctx, i))
                .collect(Collectors.toList()));
    }

    private Import normalize(ImportContext ctx, Import i) {
        switch (i.type()) {
            case Import.MvnDefinition.TYPE: {
                Import.MvnDefinition src = (Import.MvnDefinition) i;
                return Import.MvnDefinition.builder()
                        .from(src)
                        .dest(src.dest() != null ? src.dest() : DEFAULT_DEST)
                        .build();
            }
            case Import.GitDefinition.TYPE: {
                Import.GitDefinition src = (Import.GitDefinition) i;
                return normalize(ctx, src);
            }
            case Import.DirectoryDefinition.TYPE: {
                return i;
            }
            default: {
                throw new IllegalArgumentException("Unsupported import type: '" + i.type() + "'");
            }
        }
    }

    private Import.GitDefinition normalize(ImportContext ctx, Import.GitDefinition e) {
        String url = e.url();
        if (url == null) {
            String name = e.name();
            url = normalizeUrl(cfg.getSrc()) + name;
        }

        Import.SecretDefinition secret = e.secret();
        if (secret != null && secret.org() == null) {
            String secretOrgName = getOrgName(ctx);
            if (secretOrgName == null) {
                throw new IllegalStateException("Can't determine the secret's organization: '" + secret.name() + "' " +
                        "which is used in one of the 'imports': " + e);
            }

            secret = Import.SecretDefinition.builder().from(secret)
                    .org(secretOrgName)
                    .build();
        }
       
        return Import.GitDefinition.builder().from(e)
                .url(url)
                .version(e.version() != null ? e.version() : cfg.getDefaultBranch())
                .dest(e.dest() != null ? e.dest() : DEFAULT_DEST)
                .secret(secret)
                .build();
    }

    private String getOrgName(ImportContext ctx) {
        UUID projectId = ctx.projectId();
        if (projectId == null) {
            return null;
        }

        return projectDao.getOrgName(projectId);
    }

    private static String normalizeUrl(String u) {
        if (u.endsWith("/")) {
            return u;
        }
        return u + "/";
    }

    public interface ImportContext {

        static ImportContext ofProject(UUID projectId) {
            return () -> projectId;
        }

        UUID projectId();
    }
}
