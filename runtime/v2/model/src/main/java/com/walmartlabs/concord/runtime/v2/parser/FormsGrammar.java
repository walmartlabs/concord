package com.walmartlabs.concord.runtime.v2.parser;

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

import com.fasterxml.jackson.core.JsonToken;
import com.walmartlabs.concord.runtime.v2.exception.InvalidValueException;
import com.walmartlabs.concord.runtime.v2.model.*;
import io.takari.parc.Parser;
import io.takari.parc.Seq;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.runtime.v2.parser.ExpressionGrammar.maybeExpression;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;
import static io.takari.parc.Combinators.*;

public final class FormsGrammar {

    private static final Parser<Atom, FormField> formField =
            orError(betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    satisfyToken(JsonToken.FIELD_NAME).bind(a ->
                            value.map(optionsValue -> {
                                if (optionsValue.getType() != YamlValueType.OBJECT) {
                                    // will throw exception
                                    optionsValue.getValue(YamlValueType.FORM_FIELD);
                                }
                                YamlObject options = (YamlObject) optionsValue;
                                return FormFieldParser.parse(a.name, a.location, options);
                            }))),
                    YamlValueType.FORM_FIELD);

    private static final Parser<Atom, List<FormField>> formFieldsArray =
            betweenTokens(JsonToken.START_ARRAY, JsonToken.END_ARRAY, many1(formField).map(Seq::toList));

    private static final Parser<Atom, List<FormField>> formFields =
            orError(formFieldsArray, YamlValueType.ARRAY_OF_FORM_FIELD);

    private static final Parser<Atom, Form> form =
            satisfyAnyField(YamlValueType.FORM, f ->
                    formFields.map(fields -> Form.builder()
                            .name(f.name)
                            .fields(fields)
                            .location(f.location)
                            .build()));

    private static final Parser<Atom, Map<String, Form>> forms =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    many(form).map(Seq::toList)
                            .map(f -> f.stream().collect(Collectors.toMap(FormsGrammar::assertFormName, Function.identity()))));

    private static String assertFormName(Form form) {
        // Form names in project doc root 'forms' configs are not expressions. We can
        // validate while linting before execution begins
        if (!form.name().matches("^[A-Za-z0-9_ $]+$")) {
            throw InvalidValueException.builder()
                    .location(form.location())
                    .actual(form.name())
                    .expected("String matching regex \"^[A-Za-z0-9_ $]+$\"")
                    .build();
        }

        return form.name();
    }

    private static Parser<Atom, ImmutableFormCallOptions.Builder> formCallFieldsOption(ImmutableFormCallOptions.Builder o) {
        return orError(or(formFieldsArray.map(o::fields), maybeExpression.map(o::fieldsExpression)), YamlValueType.FORM_CALL_FIELDS);
    }

    private static Parser<Atom, ImmutableFormCallOptions.Builder> formCallValuesOption(ImmutableFormCallOptions.Builder o) {
        return orError(or(maybeMap.map(o::values), maybeExpression.map(o::valuesExpression)), YamlValueType.FORM_CALL_VALUES);
    }

    private static Parser<Atom, ImmutableFormCallOptions.Builder> formCallRunAsOption(ImmutableFormCallOptions.Builder o) {
        return orError(or(maybeMap.map(o::runAs), maybeExpression.map(o::runAsExpression)), YamlValueType.FORM_CALL_RUN_AS);
    }

    private static final Parser<Atom, FormCallOptions> formCallOptions =
            with(FormCallOptions::builder,
                    o -> options(
                            optional("yield", booleanVal.map(o::isYield)),
                            optional("saveSubmittedBy", booleanVal.map(o::saveSubmittedBy)),
                            optional("runAs", formCallRunAsOption(o)),
                            optional("values", formCallValuesOption(o)),
                            optional("fields", formCallFieldsOption(o))
                    ))
                    .map(ImmutableFormCallOptions.Builder::build);

    public static final Parser<Atom, FormCall> callForm =
            satisfyField("form", YamlValueType.FORM_CALL, a ->
                    stringVal.bind(formName ->
                            formCallOptions.map(options -> new FormCall(a.location, formName, options))));

    public static final Parser<Atom, Map<String, Form>> formsVal =
            orError(forms, YamlValueType.FORMS);

    private FormsGrammar() {
    }
}
