package com.walmartlabs.concord.runtime.v25.model;

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

import com.walmartlabs.concord.runtime.model.Form;
import com.walmartlabs.concord.runtime.model.SourceMap;

import javax.el.CompositeELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.FunctionMapper;
import javax.el.ValueExpression;
import javax.el.VariableMapper;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

/**
 * Java-17-compatible semantic validation shared by the v2.5 local runner and CLI lint command.
 */
public final class PlanValidator25 {

    private final ValueValidator valueValidator;

    public PlanValidator25() {
        this(new ElValueValidator());
    }

    public PlanValidator25(ValueValidator valueValidator) {
        this.valueValidator = valueValidator;
    }

    public void validate(Definition25 definition) {
        validateDefinition(definition, "$");
        definition.profiles().forEach((name, profile) -> {
            var path = "$.profiles." + name;
            compile(profile.configuration().values(), profile.configuration().sourceRange(), path + ".configuration");
            profile.flows().values().forEach(flow -> validateSteps(flow.steps()));
            validateForms(profile.forms(), path + ".forms");
        });
        var effective = definition.effective(definition.configuration().activeProfiles());
        effective.flows().values().forEach(flow -> validateCalls(flow.steps(), effective));
    }

    private void validateDefinition(Definition25 definition, String path) {
        compile(definition.configuration().values(), definition.configuration().sourceRange(), path + ".configuration");
        definition.flows().values().forEach(flow -> validateSteps(flow.steps()));
        validateForms(definition.formDefinitions(), path + ".forms");
        for (var i = 0; i < definition.triggers().size(); i++) {
            var trigger = definition.triggers().get(i);
            var triggerPath = path + ".triggers[" + i + "]";
            var range = range(trigger.sourceMap());
            compile(trigger.arguments(), range, triggerPath + ".arguments");
            compile(trigger.conditions(), range, triggerPath + ".conditions");
            compile(trigger.configuration(), range, triggerPath + ".configuration");
        }
    }

    private void validateSteps(List<Step25> steps) {
        for (var step : steps) {
            compile(step.value(), step.valueRange(), step.path() + "." + step.type());
            step.options().forEach((name, value) ->
                    compile(value, step.optionRanges().getOrDefault(name, step.sourceRange()), step.path() + "." + name));
            step.branches().forEach((name, children) -> {
                compile(name, step.sourceRange(), step.path() + "." + name);
                validateSteps(children);
            });
        }
    }

    private void validateForms(Map<String, Form> forms, String path) {
        forms.forEach((name, form) -> {
            if (!(form instanceof Form25 definition)) {
                return;
            }
            var formPath = path + "." + name;
            compile(definition.fieldsExpression(), definition.sourceRange(), formPath);
            for (var field : definition.fields()) {
                var range = range(field.location(), definition.sourceRange());
                var fieldPath = formPath + "." + field.name();
                compile(field.defaultValue(), range, fieldPath + ".default");
                compile(field.allowedValue(), range, fieldPath + ".allow");
                compile(field.options(), range, fieldPath);
            }
        });
    }

    private void validateCalls(List<Step25> steps, Definition25 definition) {
        for (var step : steps) {
            if ("call".equals(step.type()) && step.value() instanceof String name && !name.contains("${")
                    && !definition.flows().containsKey(name)) {
                throw new ModelException(List.of(new Diagnostic("V25_PLAN", Diagnostic.Severity.ERROR,
                        "Unknown flow '" + name + "'", step.sourceRange(), step.path(), null)));
            }
            step.branches().values().forEach(branch -> validateCalls(branch, definition));
        }
    }

    private void compile(Object value, SourceRange range, String path) {
        try {
            valueValidator.validate(value);
        } catch (RuntimeException e) {
            if (range == null) {
                throw e;
            }
            throw new ModelException(List.of(new Diagnostic("V25_EXPRESSION", Diagnostic.Severity.ERROR,
                    "Invalid expression: " + e.getMessage(), range, path, null)));
        }
    }

    private static SourceRange range(SourceMap source) {
        return range(source, null);
    }

    private static SourceRange range(SourceMap source, SourceRange fallback) {
        if (source == null) {
            return fallback;
        }
        return new SourceRange(source.source(), source.line(), source.column(), source.line(), source.column());
    }

    @FunctionalInterface
    public interface ValueValidator {
        void validate(Object value);
    }

    private static final class ElValueValidator implements ValueValidator {

        private final ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
        private final ELContext context = new ParseContext();

        @Override
        public void validate(Object value) {
            if (value instanceof String text && text.contains("${")) {
                expressionFactory.createValueExpression(context, text, Object.class);
            } else if (value instanceof Map<?, ?> map) {
                map.forEach((key, item) -> {
                    validate(key);
                    validate(item);
                });
            } else if (value instanceof Iterable<?> values) {
                values.forEach(this::validate);
            } else if (value != null && value.getClass().isArray()) {
                for (var i = 0; i < Array.getLength(value); i++) {
                    validate(Array.get(value, i));
                }
            }
        }
    }

    private static final class ParseContext extends ELContext {

        private final ELResolver resolver = new CompositeELResolver();
        private final VariableMapper variables = new VariableMapper() {
            @Override
            public ValueExpression resolveVariable(String variable) {
                return null;
            }

            @Override
            public ValueExpression setVariable(String variable, ValueExpression expression) {
                return null;
            }
        };
        private final FunctionMapper functions = new FunctionMapper() {
            @Override
            public java.lang.reflect.Method resolveFunction(String prefix, String localName) {
                return prefix == null || prefix.isEmpty() ? ParseFunctions.resolve(localName) : null;
            }
        };

        @Override
        public ELResolver getELResolver() {
            return resolver;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return functions;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return variables;
        }
    }

    private static final class ParseFunctions {

        private static java.lang.reflect.Method resolve(String name) {
            try {
                return switch (name) {
                    case "allVariables", "currentFlowName", "isDryRun", "isDebug", "uuid" ->
                            ParseFunctions.class.getMethod("none");
                    case "hasVariable", "hasNonNullVariable", "hasFlow", "sensitive", "evalAsMap", "throw" ->
                            ParseFunctions.class.getMethod("one", Object.class);
                    case "orDefault" -> ParseFunctions.class.getMethod("two", Object.class, Object.class);
                    default -> null;
                };
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }

        public static Object none() {
            return null;
        }

        public static Object one(Object value) {
            return value;
        }

        public static Object two(Object first, Object second) {
            return second;
        }
    }
}
