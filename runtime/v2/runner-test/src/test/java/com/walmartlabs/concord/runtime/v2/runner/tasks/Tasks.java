package com.walmartlabs.concord.runtime.v2.runner.tasks;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.runtime.v2.runner.TestRuntimeV2;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Tasks {

    private static final Logger log = LoggerFactory.getLogger(Tasks.class);

    @Named("testDefaults")
    static class TestDefaults implements Task {

        private final Variables defaults;

        @Inject
        public TestDefaults(Context ctx) {
            this.defaults = ctx.defaultVariables();
        }

        @Override
        public TaskResult execute(Variables input) {
            System.out.println("defaultsMap:" + defaults.toMap());
            return TaskResult.success();
        }

        @Value.Immutable
        @Value.Style(jdkOnly = true)
        @JsonSerialize(as = ImmutableDefaults.class)
        @JsonDeserialize(as = ImmutableDefaults.class)
        public interface Defaults {

            String a();

            @Nullable
            String b();
        }
    }

    @Named("wrapExpression")
    @SuppressWarnings("unused")
    static class WrapExpressionTask implements Task {

        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.success()
                    .value("expression", "${" + input.get("expression") + "}");
        }
    }

    @Named("testTask")
    @SuppressWarnings("unused")
    static class TestTask implements Task {

        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.success()
                    .values(input.toMap());
        }
    }

    @Named("resultTask")
    @SuppressWarnings("unused")
    public static class ResultTask implements Task {

        private final Context context;

        @Inject
        public ResultTask(Context context) {
            this.context = context;
        }

        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.success()
                    .value("result", input.get("result"));
        }

        public Object get(String path) {
            String[] p = path.split("\\.");
            Map<String, Object> m = context.variables().getMap(p[0], Collections.emptyMap());
            p = Arrays.copyOfRange(p, 1, p.length);
            return ConfigurationUtils.get(m, p);
        }
    }

    @Named("loggingExample")
    @SuppressWarnings("unused")
    public static class LoggingExampleTask implements Task {

        private static final Logger log = LoggerFactory.getLogger(LoggingExampleTask.class);
        private static final Logger processLog = LoggerFactory.getLogger("processLog");

        public void logString(String msg) {
            System.out.println(msg);
        }

        @Override
        public TaskResult execute(Variables input) throws Exception {
            log.info("This goes into a regular log");
            processLog.info("This is a processLog entry");
            System.out.println("This goes directly into the stdout");

            ExecutorService executor = Executors.newCachedThreadPool();

            for (int i = 0; i < 5; i++) {
                final int n = i;
                executor.submit(() -> {
                    Logger log = LoggerFactory.getLogger("taskThread" + n);
                    log.info("Hey, I'm a task thread #" + n);
                });
            }

            executor.shutdown();
            executor.awaitTermination(100, TimeUnit.SECONDS);

            return TaskResult.success();
        }
    }

    @Named("unknownMethod")
    @SuppressWarnings("unused")
    static class UnknownMethodTask implements Task {

        public String sayHello() {
            return "Hello!";
        }
    }

    @Named("faultyTask")
    @SuppressWarnings("unused")
    public static class FaultyTask implements Task {

        @Override
        public TaskResult execute(Variables input) {
            log.info("will fail with error");
            return TaskResult.fail("boom!")
                    .value("key", "value");
        }

        public void fail(String msg) {
            throw new UserDefinedException(msg);
        }

        public void exception(String msg) throws Exception {
            throw new Exception(msg);
        }
    }

    @Named("faultyTask2")
    @SuppressWarnings("unused")
    static class FaultyTask2 implements Task {

        @Override
        public TaskResult execute(Variables input) {
            throw new RuntimeException("boom!");
        }
    }

    @Named("faultyTask3")
    @SuppressWarnings("unused")
    static class FaultyTask3 implements Task {

        @Override
        public TaskResult execute(Variables input) throws Exception {
            throw new Exception("boom!");
        }
    }

    @Named("faultyOnceTask")
    @SuppressWarnings("unused")
    static class FaultyOnceTask implements Task {

        private static final Logger log = LoggerFactory.getLogger(FaultyOnceTask.class);

        private static final AtomicBoolean toggle = new AtomicBoolean(false);

        @Override
        public TaskResult execute(Variables input) {
            if (!toggle.getAndSet(true)) {
                log.info("faultyOnceTask: fail");
                throw new RuntimeException("boom!");
            }

            log.info("faultyOnceTask: ok");
            return TaskResult.success();
        }
    }

    @Named("neverFailTask")
    @SuppressWarnings("unused")
    static class NeverFailTask implements Task {

        private static final Logger log = LoggerFactory.getLogger(NeverFailTask.class);

        @Override
        public TaskResult execute(Variables input) {
            log.info("neverFailTask: ok");
            return TaskResult.success();
        }
    }

    @Named("conditionallyFailTask")
    @SuppressWarnings("unused")
    static class ConditionallyFailTask implements Task {

        private static final Logger log = LoggerFactory.getLogger(NeverFailTask.class);

        @Override
        public TaskResult execute(Variables input) {
            if (input.getBoolean("fail", false)) {
                log.info("ConditionallyFailTask: fail");
                throw new RuntimeException("boom!");
            }

            log.info("ConditionallyFailTask: ok");

            return TaskResult.success();
        }
    }

    @Named("simpleMethodTask")
    @SuppressWarnings("unused")
    public static class SimpleMethodTask implements Task {

        public int getValue() {
            return 42;
        }

        public int getDerivedValue(int value) {
            return value + 42;
        }
    }

    @Named("sensitiveTask")
    public static class TaskWithSensitiveData extends AbstractMap<String, String> implements Task {

        @SensitiveData
        public String getSensitive(String str) {
            return str;
        }

        @SensitiveData
        public Map<String, String> getSensitiveMap(String str) {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("nonSecretButMasked", "some value");
            result.put("secret", str);
            return result;
        }

        @SensitiveData(keys = {"secret"})
        public Map<String, String> getSensitiveMapStrict(String str) {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("nonSecret", "non secret value");
            result.put("secret", str);
            return result;
        }

        public String getPlain(String str) {
            return str;
        }

        @Override
        @SensitiveData
        public String get(Object key) {
            return key + "-value";
        }

        @Override
        public Set<Entry<String, String>> entrySet() {
            return null;
        }
    }

    @Named("injectorTestBean")
    static class InjectorTestBean {

        private final Context ctx;

        @Inject
        public InjectorTestBean(Context ctx) {
            this.ctx = ctx;
        }
    }

    @Named("injectorTestTask")
    static class InjectorTestTask implements Task {

        private final Map<String, InjectorTestBean> testBeans;

        @Inject
        public InjectorTestTask(Map<String, InjectorTestBean> testBeans) {
            this.testBeans = testBeans;
        }

        @Override
        public TaskResult execute(Variables input) {
            testBeans.forEach((k, v) -> v.ctx.workingDirectory());
            return TaskResult.success()
                    .value("x", testBeans.size());
        }
    }

    @Named("threadLocals")
    public static class ThreadLocalsTask implements Task {

        private final Context ctx;

        @Inject
        public ThreadLocalsTask(Context ctx) {
            this.ctx = ctx;
        }

        public void put(String key, Object value) {
            ctx.execution().state().setThreadLocal(ctx.execution().currentThreadId(), key, (Serializable) value);
        }

        public Serializable get(String key) {
            return ctx.execution().state().getThreadLocal(ctx.execution().currentThreadId(), key);
        }

        public void remove(String key) {
            ctx.execution().state().removeThreadLocal(ctx.execution().currentThreadId(), key);
        }
    }

    @Named("suspendTask")
    @SuppressWarnings("unused")
    static class SuspendTask implements Task {
        @Override
        public TaskResult execute(Variables input) {
            log.info("will suspend with event: '{}'", input.assertString("eventName"));

            return TaskResult.suspend(input.assertString("eventName"));
        }
    }
}
