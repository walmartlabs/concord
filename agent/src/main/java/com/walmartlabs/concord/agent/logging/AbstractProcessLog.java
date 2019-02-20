package com.walmartlabs.concord.agent.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.common.LogUtils;

public abstract class AbstractProcessLog implements ProcessLog {

    @Override
    public void delete() {
        // do nothing
    }

    @Override
    public void info(String log, Object... args) {
        log(LogUtils.formatMessage(LogUtils.LogLevel.INFO, log, args));
    }

    @Override
    public void warn(String log, Object... args) {
        log(LogUtils.formatMessage(LogUtils.LogLevel.WARN, log, args));
    }

    @Override
    public void error(String log, Object... args) {
        log(LogUtils.formatMessage(LogUtils.LogLevel.ERROR, log, args));
    }

    protected abstract void log(String message);
}
