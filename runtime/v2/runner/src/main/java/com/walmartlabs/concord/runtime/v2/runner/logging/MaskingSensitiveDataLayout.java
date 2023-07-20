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
import com.walmartlabs.concord.runtime.v2.runner.SensitiveDataHolder;

import java.util.Collection;

public class MaskingSensitiveDataLayout extends PatternLayout {

    @Override
    public String doLayout(ILoggingEvent event) {
        Collection<String> sensitiveData = SensitiveDataHolder.getInstance().get();
        String msg = super.doLayout(event);
        for (String d : sensitiveData) {
            msg = msg.replace(d, "******");
        }
        return msg;
    }
}
