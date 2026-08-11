package com.walmartlabs.concord.agent.cfg;

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
