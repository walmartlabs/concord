package com.walmartlabs.concord.plugins.ansible;

public interface PlaybookProcessBuilderFactory {

    PlaybookProcessBuilder build(String playbookPath, String inventoryPath);
}
