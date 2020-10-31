package com.walmartlabs.concord.plugins.log;

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

import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Named;
import java.io.Serializable;

@Named("log")
public class LoggingTaskV2 implements Task {

    @Override
    public TaskResult execute(Variables variables) {
        Object msg = variables.get("msg");

        String logLevel = variables.getString("level", "INFO");
        switch (logLevel.toUpperCase()) {
            case "DEBUG": {
                LogUtils.debug(msg);
                break;
            }
            case "INFO": {
                LogUtils.info(msg);
                break;
            }
            case "WARN": {
                LogUtils.warn(msg);
                break;
            }
            case "ERROR": {
                LogUtils.error(msg);
                break;
            }
            default:
                LogUtils.info(msg);
        }

        return TaskResult.success();
    }

    public static void info(String s) {
        LogUtils.info(s);
    }

    public static void info(Object o) {
        LogUtils.info(o);
    }

    public static void debug(String s) {
        LogUtils.debug(s);
    }

    public static void debug(Object o) {
        LogUtils.debug(o);
    }

    public static void warn(String s) {
        LogUtils.warn(s);
    }

    public static void warn(Object o) {
        LogUtils.warn(o);
    }

    public static void error(String s) {
        LogUtils.error(s);
    }

    public static void error(Object o) {
        LogUtils.error(o);
    }

    public void call(String s) {
        LogUtils.info(s);
    }
}
