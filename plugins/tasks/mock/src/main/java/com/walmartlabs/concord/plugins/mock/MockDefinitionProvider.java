package com.walmartlabs.concord.plugins.mock;

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

import com.walmartlabs.concord.plugins.mock.matcher.ArgsMatcher;
import com.walmartlabs.concord.runtime.v2.model.AbstractStep;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogUtils;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Singleton
public class MockDefinitionProvider {

    private static final Logger log = LoggerFactory.getLogger(MockDefinitionProvider.class);

    private final MockDefinitionMatcher mockDefinitionMatcher = new MockDefinitionMatcher();

    public MockDefinition find(Context ctx, String taskName, Variables input) {
        return findMockDefinitions(ctx, mock ->
                mockDefinitionMatcher.matches(MockDefinitionContext.task(ctx.execution().currentStep(), taskName, input), mock));
    }

    public MockDefinition find(Context ctx, String taskName, String method, Object[] params) {
        return findMockDefinitions(ctx, mock ->
                mockDefinitionMatcher.matches(MockDefinitionContext.method(ctx.execution().currentStep(), taskName, method, params), mock));
    }

    public boolean isTaskMocked(Context ctx, String taskName) {
        return mocks(ctx).anyMatch(mock -> ArgsMatcher.match(taskName, mock.task()));
    }

    private static MockDefinition findMockDefinitions(Context ctx, Predicate<MockDefinition> predicate) {
        var candidates = mocks(ctx).filter(predicate).toList();
        if (candidates.isEmpty()) {
            return null;
        } else if (candidates.size() == 1) {
            return candidates.get(0);
        }
        throw new UserDefinedException("Too many mocks: " + candidates);
    }

    @SuppressWarnings("unchecked")
    public static Stream<MockDefinition> mocks(Context ctx) {
        return ctx.variables().getList("mocks", List.of()).stream()
                .map(m -> new MockDefinition((Map<String, Object>) m));
    }

    public static class MockDefinitionMatcher {

        private final List<Matcher> matchers = List.of(
                new TaskNameMatcher(),
                new MethodNameMatcher(),
                new StepNameMatcher(),
                new StepMetaMatcher(),
                new TaskInputMatcher(),
                new TaskArgsMatcher()
        );

        public boolean matches(MockDefinitionContext context, MockDefinition mock) {
            for (Matcher matcher : matchers) {
                if (!matcher.matches(context, mock)) {
                    return false;
                }
            }
            return true;
        }
    }

    public interface Matcher {

        boolean matches(MockDefinitionContext context, MockDefinition mock);
    }

    public static class TaskNameMatcher implements Matcher {

        @Override
        public boolean matches(MockDefinitionContext context, MockDefinition mock) {
            return ArgsMatcher.match(context.taskName(), mock.task());
        }
    }

    public static class MethodNameMatcher implements Matcher {

        @Override
        public boolean matches(MockDefinitionContext context, MockDefinition mock) {
            if (context.method() == null) {
                return true;
            }

            return ArgsMatcher.match(context.method(), mock.method());
        }
    }

    public static class StepNameMatcher implements Matcher {

        @Override
        public boolean matches(MockDefinitionContext context, MockDefinition mock) {
            if (mock.stepName() == null) {
                return true;
            }

            var logContext = LogUtils.getContext();
            return logContext != null && ArgsMatcher.match(logContext.segmentName(), mock.stepName());
        }
    }

    public static class StepMetaMatcher implements Matcher {

        @Override
        public boolean matches(MockDefinitionContext context, MockDefinition mock) {
            if (mock.stepMeta().isEmpty()) {
                return true;
            }

            if (!(context.currentStep() instanceof AbstractStep<?> step)) {
                return false;
            }

            var stepOptions = step.getOptions();
            if (stepOptions == null) {
                return false;
            }

            return ArgsMatcher.match(stepOptions.meta(), mock.stepMeta());
        }
    }

    public static class TaskInputMatcher implements Matcher {

        @Override
        public boolean matches(MockDefinitionContext context, MockDefinition mock) {
            if (context.input() == null) {
                return true;
            }

            return ArgsMatcher.match(context.input().toMap(), mock.input());
        }
    }

    public static class TaskArgsMatcher implements Matcher {

        @Override
        public boolean matches(MockDefinitionContext context, MockDefinition mock) {
            if (context.params() == null) {
                return true;
            }

            return ArgsMatcher.match(context.params(), mock.args());
        }
    }
}
