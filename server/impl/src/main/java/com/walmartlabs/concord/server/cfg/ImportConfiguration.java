package com.walmartlabs.concord.server.cfg;

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

import com.walmartlabs.ollie.config.Config;
import org.eclipse.sisu.Nullable;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;

import static com.walmartlabs.concord.server.cfg.Utils.getPath;

@Named
@Singleton
public class ImportConfiguration {

    @Inject
    @Config("imports.src")
    private String src;

    private final Path cacheDir;

    @Inject
    public ImportConfiguration(@Config("imports.cacheDir") @Nullable String cacheDir) throws IOException {
        this.cacheDir = getPath(cacheDir, "importsCache");
    }

    public String getSrc() {
        return src;
    }

    public Path getCacheDir() {
        return cacheDir;
    }
}
