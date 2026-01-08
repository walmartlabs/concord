package com.walmartlabs.concord.server.cfg;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ConsoleConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ConsoleConfiguration.class);

    private final String cfg;
    private final boolean useDefaultCfg;

    @Inject
    public ConsoleConfiguration(@Nullable @Config("console.cfgFile") String cfgFile) throws IOException {
        if (cfgFile == null) {
            this.cfg = "";
            this.useDefaultCfg = true;
        } else {
            var path = Paths.get(cfgFile);
            if (!Files.isReadable(path)) {
                throw new IOException("The console cfgFile is not readable or doesn't exist: " + cfgFile);
            }

            log.info("Using console configuration from {}", path.normalize().toAbsolutePath());
            try (var in = Files.newInputStream(path)) {
                this.cfg = new String(in.readAllBytes(), UTF_8);
                this.useDefaultCfg = false;
            }
        }
    }

    public String getCfg() {
        return cfg;
    }

    public boolean isUseDefaultCfg() {
        return useDefaultCfg;
    }
}
