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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javax.inject.Named;

@Named("log")
public class LoggingTaskV2 implements Task {

    private static final ObjectMapper yamlObjectMapper = createYamlObjectMapper();

    @Override
    public TaskResult execute(Variables variables) {
        Object msg = variables.get("msg");
        String format = variables.getString("format");

        String logLevel = variables.getString("level", "INFO");
        switch (logLevel.toUpperCase()) {
            case "DEBUG": {
                LogUtils.debug(formatMessage(format, msg));
                break;
            }
            case "INFO": {
                LogUtils.info(formatMessage(format, msg));
                break;
            }
            case "WARN": {
                LogUtils.warn(formatMessage(format, msg));
                break;
            }
            case "ERROR": {
                LogUtils.error(formatMessage(format, msg));
                break;
            }
            default:
                LogUtils.info(formatMessage(format, msg));
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

    private static Object formatMessage(String format, Object msg) {
        if (format == null || format.trim().isEmpty()) {
            return msg;
        }

        if ("yaml".equalsIgnoreCase(format)) {
            try {
                return "\n" + yamlObjectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(msg);
            } catch (Exception e) {
                throw new RuntimeException("Invalid yaml:" + e.getMessage());
            }
        }

        throw new IllegalArgumentException("Unknown format '" + format + "'");
    }

    private static ObjectMapper createYamlObjectMapper() {
        return defaultObjectMapper(new YAMLFactory()
                                   .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                                   .disable(YAMLGenerator.Feature.SPLIT_LINES));
    }

    private static ObjectMapper defaultObjectMapper(JsonFactory jf) {
        ObjectMapper om = new ObjectMapper(jf);
        om.registerModule(new Jdk8Module());
        om.registerModule(new JavaTimeModule());
        return om;
    }
}
