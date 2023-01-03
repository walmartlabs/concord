package com.walmartlabs.concord.agent.executors.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.agent.logging.ProcessLog;
import com.walmartlabs.concord.agent.logging.RedirectedProcessLog;
import com.walmartlabs.concord.agent.logging.RemoteProcessLog;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

public class RunnerLog implements ProcessLog {

    private final RedirectedProcessLog redirectedLog;
    private final RemoteProcessLog remoteLog;

    public RunnerLog(RedirectedProcessLog redirectedLog, RemoteProcessLog remoteLog) {
        this.redirectedLog = redirectedLog;
        this.remoteLog = remoteLog;
    }

    public void run(Supplier<Boolean> stopCondition) throws Exception {
        redirectedLog.run(stopCondition);
    }

    @Override
    public void delete() {
        redirectedLog.delete();
        remoteLog.delete();
    }

    @Override
    public void log(InputStream src) throws IOException {
        redirectedLog.log(src);
    }

    @Override
    public void info(String log, Object... args) {
        remoteLog.info(log, args);
    }

    @Override
    public void warn(String log, Object... args) {
        remoteLog.warn(log, args);
    }

    @Override
    public void error(String log, Object... args) {
        remoteLog.error(log, args);
    }
}
