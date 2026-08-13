package com.walmartlabs.concord.agent.cfg;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.typesafe.config.Config;
import com.walmartlabs.concord.common.cfg.UnzipLimits;

import javax.inject.Inject;

public class UnzipLimitsConfiguration extends UnzipLimits {

    @Inject
    public UnzipLimitsConfiguration(Config cfg) {
        super(
                cfg.getLong("zip.unzipLimits.maxEntries"),
                cfg.getLong("zip.unzipLimits.maxTotalUncompressedBytes"),
                cfg.getLong("zip.unzipLimits.maxEntryUncompressedBytes"),
                cfg.getInt("zip.unzipLimits.maxCompressionRatio")
        );
    }
}
