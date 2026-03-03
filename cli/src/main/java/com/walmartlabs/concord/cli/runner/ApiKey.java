package com.walmartlabs.concord.cli.runner;

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

import com.walmartlabs.concord.cli.CliConfig;
import com.walmartlabs.concord.cli.Verbosity;
import com.walmartlabs.concord.cli.secrets.CliSecretService;

import java.nio.file.Path;

public record ApiKey(String value) {

    public static ApiKey create(CliConfig.CliConfigContext cliConfigContext, Path workDir, Verbosity verbosity) {
        CliConfig.RemoteRunConfiguration remoteRunConfig = cliConfigContext.remoteRun();
        if (remoteRunConfig == null || remoteRunConfig.apiKeyRef() == null) {
            return new ApiKey(null);
        }

        if (verbosity.verbose()) {
            System.out.println("Using '" + remoteRunConfig.apiKeyRef() + "' as a secret for API key");
        }

        CliSecretService secretService = CliSecretService.create(cliConfigContext, workDir, verbosity);
        try {
            return new ApiKey(secretService.exportAsString(remoteRunConfig.apiKeyRef().orgName(), remoteRunConfig.apiKeyRef().secretName(), null));
        } catch (Exception e) {
            throw new RuntimeException("Unable to fetch the API key. " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "***";
    }
}
