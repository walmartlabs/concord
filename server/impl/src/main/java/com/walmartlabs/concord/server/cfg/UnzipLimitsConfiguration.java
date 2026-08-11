package com.walmartlabs.concord.server.cfg;

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
