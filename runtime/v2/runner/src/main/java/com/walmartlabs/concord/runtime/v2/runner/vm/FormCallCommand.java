package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.forms.Form;
import com.walmartlabs.concord.forms.FormOptions;
import com.walmartlabs.concord.runtime.common.FormService;
import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.parser.FormFieldParser;
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContext;
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import com.walmartlabs.concord.svm.ThreadStatus;

import java.io.Serializable;
import java.util.*;

public class FormCallCommand extends StepCommand<FormCall> {

    private static final long serialVersionUID = 1L;

    public FormCallCommand(FormCall formCall) {
        super(formCall);
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        String eventRef = UUID.randomUUID().toString();

        Context ctx = runtime.getService(Context.class);

        ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);
        EvalContext evalContext = EvalContextFactory.global(ctx);

        FormCall call = getStep();
        String formName = expressionEvaluator.eval(evalContext, call.getName(), String.class);

        ProcessDefinition processDefinition = runtime.getService(ProcessDefinition.class);
        ProcessConfiguration processConfiguration = runtime.getService(ProcessConfiguration.class);

        List<FormField> fields = assertFormFields(expressionEvaluator, evalContext, processConfiguration, processDefinition, formName, call);
        Form form = Form.builder()
                .name(formName)
                .eventName(eventRef)
                .options(buildFormOptions(expressionEvaluator, evalContext, call))
                .fields(buildFormFields(expressionEvaluator, evalContext, fields, Objects.requireNonNull(call.getOptions()).values()))
                .build();

        FormService formService = runtime.getService(FormService.class);
        formService.save(form);

        state.peekFrame(threadId).pop();
        state.setEventRef(threadId, eventRef);
        state.setStatus(threadId, ThreadStatus.SUSPENDED);
    }

    private static FormOptions buildFormOptions(ExpressionEvaluator expressionEvaluator, EvalContext ctx, FormCall formCall) {
        FormCallOptions options = Objects.requireNonNull(formCall.getOptions());

        Map<String, Serializable> runAs = expressionEvaluator.evalAsMap(ctx, options.runAs());

        return FormOptions.builder()
                .yield(options.yield())
                .saveSubmittedBy(options.saveSubmittedBy())
                .runAs(runAs)
                .build();
    }

    private static List<com.walmartlabs.concord.forms.FormField> buildFormFields(ExpressionEvaluator expressionEvaluator, EvalContext ctx, List<com.walmartlabs.concord.runtime.v2.model.FormField> fields, Map<String, Serializable> values) {
        List<com.walmartlabs.concord.forms.FormField> result = new ArrayList<>();

        fields.forEach(f -> {
            Serializable defaultValue = null;
            Serializable value = values.get(f.name());
            if (value != null) {
                defaultValue = expressionEvaluator.eval(ctx, f.defaultValue(), Serializable.class);
            } else {
                if (f.defaultValue() != null) {
                    defaultValue = expressionEvaluator.eval(ctx, f.defaultValue(), Serializable.class);
                }
            }

            Serializable allowedValue = null;
            if (f.allowedValue() != null) {
                allowedValue = expressionEvaluator.eval(ctx, f.allowedValue(), Serializable.class);
            }

            String label = expressionEvaluator.eval(ctx, f.label(), String.class);

            result.add(com.walmartlabs.concord.forms.FormField.builder()
                    .name(f.name())
                    .label(label)
                    .type(f.type())
                    .cardinality(f.cardinality())
                    .defaultValue(defaultValue)
                    .allowedValue(allowedValue)
                    .options(f.options())
                    .build());
        });

        return result;
    }

    private static List<FormField> assertFormFields(ExpressionEvaluator expressionEvaluator, EvalContext ctx,
                                                    ProcessConfiguration processConfiguration,
                                                    ProcessDefinition pd,
                                                    String formName,
                                                    FormCall formCall) {
        FormCallOptions options = Objects.requireNonNull(formCall.getOptions());
        if (!options.fields().isEmpty()) {
            return options.fields();
        }

        List<Map<String, Map<String, Object>>> rawFields = expressionEvaluator.evalAsList(ctx, options.fieldsExpression());
        if (rawFields != null) {
            return FormFieldParser.parse(formCall.getLocation(), rawFields);
        }

        com.walmartlabs.concord.runtime.v2.model.Form fd = pd.forms().get(formName);
        for (String activeProfile : processConfiguration.processInfo().activeProfiles()) {
            com.walmartlabs.concord.runtime.v2.model.Form maybeForm = pd.profiles().getOrDefault(activeProfile, Profile.builder().build()).forms().get(formName);
            if (maybeForm != null) {
                fd = maybeForm;
            }
        }

        if (fd != null) {
            return fd.fields();
        }

        throw new IllegalStateException("Form not found: " + formName);
    }
}
