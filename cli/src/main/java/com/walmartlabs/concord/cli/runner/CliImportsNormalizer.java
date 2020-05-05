package com.walmartlabs.concord.cli.runner;

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

import com.walmartlabs.concord.imports.Import;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.runtime.v2.ImportsNormalizer;

import java.util.stream.Collectors;

public class CliImportsNormalizer implements ImportsNormalizer {

    private static final String DEFAULT_VERSION = "master";
    private static final String DEFAULT_DEST = "concord";

    private final String defaultSource;
    private final boolean verbose;

    public CliImportsNormalizer(String defaultSource, boolean verbose) {
        this.defaultSource = defaultSource;
        this.verbose = verbose;
    }

    @Override
    public Imports normalize(Imports imports) {
        if (imports == null || imports.isEmpty()) {
            return Imports.builder().build();
        }

        if (verbose) {
            System.out.println("Processing imports...");
        }

        Imports result = Imports.of(imports.items().stream()
                .map(this::normalize)
                .collect(Collectors.toList()));

        if (verbose) {
            imports.items().forEach(i -> System.out.println("import: " + i));
        }

        return result;
    }

    private Import normalize(Import i) {
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
                return normalize(src);
            }
            default: {
                throw new IllegalArgumentException("Unsupported import type: '" + i.type() + "'");
            }
        }
    }

    private Import.GitDefinition normalize(Import.GitDefinition e) {
        String url = e.url();
        if (url == null) {
            String name = e.name();
            url = normalizeUrl(defaultSource) + name;
        }

        return Import.GitDefinition.builder().from(e)
                .url(url)
                .version(e.version() != null ? e.version() : DEFAULT_VERSION)
                .dest(e.dest() != null ? e.dest() : DEFAULT_DEST)
                .secret(e.secret())
                .build();
    }

    private static String normalizeUrl(String u) {
        if (u.endsWith("/")) {
            return u;
        }
        return u + "/";
    }
}
