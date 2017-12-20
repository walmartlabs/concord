package com.walmartlabs.concord.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import java.io.IOException;
import java.util.Map;

public interface PlaybookProcessBuilder {

    PlaybookProcessBuilder withCfgFile(String cfgFile);

    PlaybookProcessBuilder withExtraVars(Map<String, String> extraVars);

    PlaybookProcessBuilder withUser(String user);

    PlaybookProcessBuilder withTags(String tags);

    PlaybookProcessBuilder withSkipTags(String skipTags);

    PlaybookProcessBuilder withPrivateKey(String privateKey);

    PlaybookProcessBuilder withAttachmentsDir(String attachmentsDir);

    PlaybookProcessBuilder withVaultPasswordFile(String vaultPasswordFile);

    PlaybookProcessBuilder withEnv(Map<String, String> env);

    PlaybookProcessBuilder withLimit(String limit);

    PlaybookProcessBuilder withDebug(boolean debug);

    PlaybookProcessBuilder withVerboseLevel(int level);

    Process build() throws IOException;
}
