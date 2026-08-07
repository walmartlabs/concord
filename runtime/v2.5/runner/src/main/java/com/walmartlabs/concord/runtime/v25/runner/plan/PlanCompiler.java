package com.walmartlabs.concord.runtime.v25.runner.plan;

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
import com.walmartlabs.concord.runtime.v25.model.Definition25;
import com.walmartlabs.concord.runtime.v25.model.Diagnostic;
import com.walmartlabs.concord.runtime.v25.model.Form25;
import com.walmartlabs.concord.runtime.v25.model.ModelException;
import com.walmartlabs.concord.runtime.v25.model.SourceRange;
import com.walmartlabs.concord.runtime.v25.model.Step25;
import com.walmartlabs.concord.runtime.v25.runner.expression.ExpressionService;
import com.walmartlabs.concord.runtime.v25.runner.plan.ExecutionPlan.FlowPlan;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlanCompiler {

    private final ExpressionService expressions;

    public PlanCompiler(ExpressionService expressions) {
        this.expressions = expressions;
    }

    public ExecutionPlan compile(Definition25 source) {
        validate(source);
        var definition = source.effective(source.configuration().activeProfiles());
        var counter = new Counter();
        var flows = new LinkedHashMap<String, FlowPlan>();
        var flowId = 0;
        for (var entry : definition.flows().entrySet()) {
            flows.put(entry.getKey(), new FlowPlan(flowId++, entry.getKey(),
                    instructions(entry.getValue().steps(), definition, counter)));
        }
        return new ExecutionPlan(identity(definition), definition.configuration(), flows, definition.publicFlows(),
                definition.formDefinitions());
    }

    public void validate(Definition25 definition) {
        validateDefinition(definition, "$");
        definition.profiles().forEach((name, profile) -> {
            var path = "$.profiles." + name;
            compile(profile.configuration().values(), profile.configuration().sourceRange(), path + ".configuration");
            profile.flows().values().forEach(flow -> validateSteps(flow.steps()));
            validateForms(profile.forms(), path + ".forms");
        });
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
            step.options().forEach((name, value) -> {
                if (!("script".equals(step.type()) && "body".equals(name))) {
                    compile(value, step.optionRanges().getOrDefault(name, step.sourceRange()),
                            step.path() + "." + name);
                }
            });
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
                compile(field.label(), range, fieldPath + ".label");
                compile(field.defaultValue(), range, fieldPath + ".default");
                compile(field.allowedValue(), range, fieldPath + ".allow");
                compile(field.options(), range, fieldPath);
            }
        });
    }

    private void compile(Object value, SourceRange range, String path) {
        try {
            expressions.compile(value);
        } catch (ExpressionService.ExpressionException e) {
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

    private List<Instruction> instructions(List<Step25> steps, Definition25 definition, Counter counter) {
        var result = new ArrayList<Instruction>(steps.size());
        for (var step : steps) {
            var branches = new LinkedHashMap<String, List<Instruction>>();
            step.branches().forEach((name, children) ->
                    branches.put(name, instructions(children, definition, counter)));
            if ("call".equals(step.type()) && step.value() instanceof String name && !name.contains("${")
                    && !definition.flows().containsKey(name)) {
                throw new IllegalArgumentException("Unknown flow '" + name + "' at " + step.path());
            }
            result.add(new Instruction(counter.next(), Opcode.from(step.type()), step.type(), step.value(),
                    step.options(), branches, step.sourceRange(), step.path()));
        }
        return result;
    }

    private String identity(Definition25 definition) {
        var canonical = new StringBuilder();
        append(canonical, definition.configuration().values());
        definition.flows().forEach((name, flow) -> {
            appendToken(canonical, "flow");
            append(canonical, name);
            appendSteps(canonical, flow.steps());
        });
        definition.formDefinitions().forEach((name, form) -> {
            appendToken(canonical, "form");
            append(canonical, name);
            if (form instanceof Form25 dynamic) {
                append(canonical, dynamic.fieldsExpression());
            } else {
                append(canonical, null);
            }
            form.fields().forEach(field -> {
                appendToken(canonical, "field");
                append(canonical, field.name());
                append(canonical, field.type());
                append(canonical, field.label());
                append(canonical, field.defaultValue());
                append(canonical, field.allowedValue());
                append(canonical, field.options());
            });
        });
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString()
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void appendSteps(StringBuilder target, List<Step25> steps) {
        appendToken(target, "steps");
        for (var step : steps) {
            appendToken(target, "step");
            append(target, step.type());
            append(target, step.value());
            append(target, step.options());
            step.branches().forEach((name, branch) -> {
                appendToken(target, "branch");
                append(target, name);
                appendSteps(target, branch);
            });
        }
    }

    private void append(StringBuilder target, Object value) {
        if (value == null) {
            appendToken(target, "null");
        } else if (value instanceof Map<?, ?> map) {
            appendToken(target, "map");
            appendToken(target, Integer.toString(map.size()));
            map.forEach((key, item) -> {
                appendToken(target, "entry");
                append(target, key);
                append(target, item);
            });
        } else if (value instanceof Iterable<?> values) {
            var nested = new StringBuilder();
            for (var item : values) {
                append(nested, item);
            }
            appendToken(target, "list");
            appendToken(target, nested.toString());
        } else if (value.getClass().isArray()) {
            appendToken(target, "array");
            appendToken(target, Integer.toString(Array.getLength(value)));
            for (var i = 0; i < Array.getLength(value); i++) {
                append(target, Array.get(value, i));
            }
        } else {
            appendToken(target, value.getClass().getName());
            appendToken(target, value.toString());
        }
    }

    private static void appendToken(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    private static final class Counter {

        private int value;

        private int next() {
            return value++;
        }
    }
}
