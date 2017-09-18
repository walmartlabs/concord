package com.walmartlabs.concord.plugins.ansible;

import java.io.IOException;
import java.util.Map;

public interface PlaybookProcessBuilder {

    PlaybookProcessBuilder withCfgFile(String cfgFile);

    PlaybookProcessBuilder withExtraVars(Map<String, String> extraVars);

    PlaybookProcessBuilder withUser(String user);

    PlaybookProcessBuilder withTags(String tags);

    PlaybookProcessBuilder withPrivateKey(String privateKey);

    PlaybookProcessBuilder withAttachmentsDir(String attachmentsDir);

    PlaybookProcessBuilder withVaultPasswordFile(String vaultPasswordFile);

    PlaybookProcessBuilder withEnv(Map<String, String> env);

    PlaybookProcessBuilder withDebug(boolean debug);

    PlaybookProcessBuilder withVerboseLevel(int level);

    Process build() throws IOException;
}
