package com.walmartlabs.concord.runtime.v2.runner.logging;

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

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.walmartlabs.concord.runtime.common.SensitiveDataMasker;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;

import static java.util.Objects.requireNonNull;

public class CustomLayout extends PatternLayout {

    private static volatile String workDirToReplace;
    private static volatile SensitiveDataHolder sensitiveDataHolder;

    /**
     * Enables masking of ${workDir} values in logs. Such values often add noise to logs.
     */
    public static void enableWorkingDirectoryMasking(WorkingDirectory workDir) {
        requireNonNull(workDir);
        CustomLayout.workDirToReplace = workDir.getValue().toString();
    }

    public static void setSensitiveDataHolder(SensitiveDataHolder sensitiveDataHolder) {
        requireNonNull(sensitiveDataHolder);
        CustomLayout.sensitiveDataHolder = sensitiveDataHolder;
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        var msg = super.doLayout(event);
        msg = SensitiveDataMasker.mask(msg, sensitiveDataHolder.get());
        if (CustomLayout.workDirToReplace != null) {
            msg = msg.replace(workDirToReplace, "$WORK_DIR");
        }
        return msg;
    }
}
