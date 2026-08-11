package com.walmartlabs.concord.server.cfg;

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

import com.walmartlabs.concord.common.cfg.UnzipLimits;
import com.walmartlabs.concord.config.Config;

import javax.inject.Inject;

public class UnzipLimitsConfiguration extends UnzipLimits {

    @Inject
    public UnzipLimitsConfiguration(
            @Config("zip.unzipLimits.maxEntries") long maxEntries,
            @Config("zip.unzipLimits.maxTotalUncompressedBytes") long maxTotalUncompressedBytes,
            @Config("zip.unzipLimits.maxEntryUncompressedBytes") long maxEntryUncompressedBytes,
            @Config("zip.unzipLimits.maxCompressionRatio") int maxCompressionRatio
    ) {
        super(maxEntries, maxTotalUncompressedBytes, maxEntryUncompressedBytes, maxCompressionRatio);
    }
}
