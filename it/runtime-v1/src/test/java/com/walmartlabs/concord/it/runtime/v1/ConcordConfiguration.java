package com.walmartlabs.concord.it.runtime.v1;

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

import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.walmartlabs.concord.it.common.BaseConcordConfiguration;

public final class ConcordConfiguration {

    public static ConcordRule configure() {
        ConcordRule concord = BaseConcordConfiguration.createBase()
                .pathToRunnerV1("target/runner-v1.jar")
                .pathToRunnerV2(null)
                .extraConfigurationSupplier(BaseConcordConfiguration::baseConfig);

        return BaseConcordConfiguration.applyMode(concord);
    }

    private ConcordConfiguration() {
    }
}
