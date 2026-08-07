package com.walmartlabs.concord.runtime.v25.runner;

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

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.walmartlabs.concord.runtime.common.SensitiveDataMasker;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;

import java.nio.file.Path;

/** Applies the same sensitive-value and work-directory redaction to emitted process logs. */
public final class V25LogLayout extends PatternLayout {

    private static volatile SensitiveDataHolder sensitiveData;
    private static volatile String workDirectory;

    static void configure(SensitiveDataHolder holder, Path directory) {
        sensitiveData = holder;
        workDirectory = directory != null ? directory.toString() : null;
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        var message = super.doLayout(event);
        var holder = sensitiveData;
        if (holder != null) {
            message = SensitiveDataMasker.mask(message, holder.get());
        }
        var directory = workDirectory;
        return directory != null ? message.replace(directory, "$WORK_DIR") : message;
    }
}
