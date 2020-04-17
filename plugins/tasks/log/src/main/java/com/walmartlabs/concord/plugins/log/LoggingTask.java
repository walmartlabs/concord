package com.walmartlabs.concord.plugins.log;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Named;

@Named("log")
public class LoggingTask implements Task {

    public void debug(String s) {
        LogUtils.debug(s);
    }

    public void info(String s) {
        LogUtils.info(s);
    }

    /**
     * @deprecated use {@link #info(String)} or {@link #call(String)}
     */
    @Deprecated
    public void info(String logName, String s) {
        LogUtils.info(String.format("%s - %s", logName, s));
    }

    public void warn(String s) {
        LogUtils.warn(s);
    }

    /**
     * @deprecated use {@link #warn(String)} or {@link #call(String)}
     */
    @Deprecated
    public void warn(String logName, String s) {
        LogUtils.warn(String.format("%s - %s", logName, s));
    }

    public void error(String s) {
        LogUtils.error(s);
    }

    public void error(String logName, String s) {
        LogUtils.error(String.format("%s - %s", logName, s));
    }

    public void call(String s) {
        LogUtils.info(s);
    }

    @Override
    public void execute(Context ctx) {
        String msg = assertString(ctx, "msg");
        LogUtils.info(msg);
    }

    private static String assertString(Context ctx, String k) {
        Object v = ctx.getVariable(k);
        if (v == null) {
            throw new IllegalArgumentException("Required parameter '" + k + "' is missing");
        }
        return v.toString();
    }
}
