package com.walmartlabs.concord.runtime.v2.runner.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;

import javax.inject.Inject;
import javax.inject.Provider;

public class LoggerProvider implements Provider<RunnerLogger> {

    private final RunnerLogger logger;

    @Inject
    public LoggerProvider(RunnerConfiguration runnerCfg,
                          LoggingClient loggingClient) {
        this.logger = createLogger(runnerCfg, loggingClient);
    }

    @Override
    public RunnerLogger get() {
        return logger;
    }

    private static RunnerLogger createLogger(RunnerConfiguration runnerCfg, LoggingClient loggingClient) {
        if (runnerCfg.logging().segmentedLogs()) {
            return new SegmentedLogger(loggingClient);
        } else {
            return new SimpleLogger();
        }
    }
}
