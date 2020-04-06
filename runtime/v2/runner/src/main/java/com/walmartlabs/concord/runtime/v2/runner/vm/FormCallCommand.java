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
import com.walmartlabs.concord.runtime.v2.model.FormCall;
import com.walmartlabs.concord.runtime.v2.model.FormCallOptions;
import com.walmartlabs.concord.runtime.v2.model.FormField;
import com.walmartlabs.concord.runtime.v2.model.Forms;
import com.walmartlabs.concord.runtime.v2.parser.FormFieldParser;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import com.walmartlabs.concord.svm.ThreadStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FormCallCommand extends StepCommand<FormCall> {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(FormCallCommand.class);

    private final Forms formDefinitions;

    public FormCallCommand(FormCall form, Forms formDefinitions) {
        super(form);

        // TODO replace with ID, avoid serializing forms multiple times
        this.formDefinitions = formDefinitions;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        String eventRef = UUID.randomUUID().toString();

        ContextFactory contextFactory = runtime.getService(ContextFactory.class);
        ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);

        Context ctx = contextFactory.create(runtime, state, threadId, getStep());

        FormCall call = getStep();
        String formName = expressionEvaluator.eval(ctx, call.getName(), String.class);
        List<FormField> fields = assertFormFields(expressionEvaluator, ctx, formName, call);
        Form form = Form.builder()
                .name(formName)
                .eventName(eventRef)
                .options(buildFormOptions(expressionEvaluator, ctx))
                .fields(buildFormFields(expressionEvaluator, ctx, fields, call.getOptions().values()))
                .build();

        FormService formService = runtime.getService(FormService.class);
        formService.save(form);

        state.peekFrame(threadId).pop();
        state.setEventRef(threadId, eventRef);
        state.setStatus(threadId, ThreadStatus.SUSPENDED);
        log.debug("eval [{}] -> done, eventRef={}", threadId, eventRef); // TODO remove?
    }

    private FormOptions buildFormOptions(ExpressionEvaluator expressionEvaluator, Context ctx) {
        FormCallOptions options = getStep().getOptions();

        Map<String, Serializable> runAs = expressionEvaluator.evalAsMap(ctx, options.runAs());

        return FormOptions.builder()
                .yield(options.yield())
                .saveSubmittedBy(options.saveSubmittedBy())
                .runAs(runAs)
                .build();
    }

    private List<com.walmartlabs.concord.forms.FormField> buildFormFields(ExpressionEvaluator expressionEvaluator, Context ctx, List<com.walmartlabs.concord.runtime.v2.model.FormField> fields, Map<String, Serializable> values) {
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

    private List<FormField> assertFormFields(ExpressionEvaluator expressionEvaluator, Context ctx, String formName, FormCall formCall) {
        FormCallOptions options = formCall.getOptions();
        if (!options.fields().isEmpty()) {
            return options.fields();
        }

        List<Map<String, Map<String, Object>>> rawFields = expressionEvaluator.evalAsList(ctx, options.fieldsExpression());
        if (rawFields != null) {
            return FormFieldParser.parse(formCall.getLocation(), rawFields);
        }

        com.walmartlabs.concord.runtime.v2.model.Form fd = formDefinitions.get(formName);
        if (fd != null) {
            return fd.fields();
        }
        throw new RuntimeException("Can't find '" + formName + "' definition");
    }
}
