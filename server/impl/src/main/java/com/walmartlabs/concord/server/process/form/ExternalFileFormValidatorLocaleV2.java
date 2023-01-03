package com.walmartlabs.concord.server.process.form;

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

import com.walmartlabs.concord.forms.DefaultFormValidatorLocale;
import com.walmartlabs.concord.forms.FormField;
import com.walmartlabs.concord.forms.FormValidatorLocale;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;

import java.io.InputStream;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.walmartlabs.concord.forms.DefaultFormValidatorLocale.*;

public class ExternalFileFormValidatorLocaleV2 implements FormValidatorLocale {

    private final FormValidatorLocale fallback = new DefaultFormValidatorLocale();

    private final Map<String, String> messages;

    public ExternalFileFormValidatorLocaleV2(ProcessKey processKey, String formName, ProcessStateManager stateManager) {
        this.messages = loadMessages(processKey, formName, stateManager);
    }

    @Override
    public String noFieldsDefined(String formId) {
        return fallback.noFieldsDefined(formId);
    }

    @Override
    public String invalidCardinality(String formId, FormField field, Object value) {
        return getMessage("invalidCardinality", field)
                .map(m -> MessageFormat.format(m, fieldName(field, null), spell(field.cardinality()), field.cardinality(), value))
                .orElse(fallback.invalidCardinality(formId, field, value));
    }

    @Override
    public String expectedString(String formId, FormField field, Integer idx, Object value) {
        return getMessage("expectedString", field)
                .map(m -> MessageFormat.format(m, fieldName(field, idx), value))
                .orElse(fallback.expectedString(formId, field, idx, value));
    }

    @Override
    public String expectedInteger(String formId, FormField field, Integer idx, Object value) {
        return getMessage("expectedInteger", field)
                .map(m -> MessageFormat.format(m, fieldName(field, idx), value))
                .orElse(fallback.expectedInteger(formId, field, idx, value));
    }

    @Override
    public String expectedDecimal(String formId, FormField field, Integer idx, Object value) {
        return getMessage("expectedDecimal", field)
                .map(m -> MessageFormat.format(m, fieldName(field, idx), value))
                .orElse(fallback.expectedDecimal(formId, field, idx, value));
    }

    @Override
    public String expectedBoolean(String formId, FormField field, Integer idx, Object value) {
        return getMessage("expectedBoolean", field)
                .map(m -> MessageFormat.format(m, fieldName(field, idx), value))
                .orElse(fallback.expectedBoolean(formId, field, idx, value));
    }

    public String expectedDate(String formId, FormField field, Integer idx, Object value) {
        return getMessage("expectedDate", field)
                .map(m -> MessageFormat.format(m, fieldName(field, idx), value))
                .orElse(fallback.expectedDate(formId, field, idx, value));
    }

    @Override
    public String doesntMatchPattern(String formId, FormField field, Integer idx, String pattern, Object value) {
        return getMessage("doesntMatchPattern", field)
                .map(m -> MessageFormat.format(m, fieldName(field, idx), value, pattern))
                .orElse(fallback.doesntMatchPattern(formId, field, idx, pattern, value));
    }

    @Override
    public String integerRangeError(String formId, FormField field, Integer idx, Long min, Long max, Object value) {
        return getMessage("integerRangeError", field)
                .map(m -> MessageFormat.format(m, fieldName(field, idx), value, bounds(min, max), min, max))
                .orElse(fallback.integerRangeError(formId, field, idx, min, max, value));
    }

    @Override
    public String decimalRangeError(String formId, FormField field, Integer idx, Double min, Double max, Object value) {
        return getMessage("decimalRangeError", field)
                .map(m -> MessageFormat.format(m, fieldName(field, idx), value, bounds(min, max), min, max))
                .orElse(fallback.decimalRangeError(formId, field, idx, min, max, value));
    }

    @Override
    public String valueNotAllowed(String formId, FormField field, Integer idx, Object allowed, Object value) {
        return getMessage("valueNotAllowed", field)
                .map(m -> MessageFormat.format(m, fieldName(field, idx), value, allowed))
                .orElse(fallback.valueNotAllowed(formId, field, idx, allowed, value));
    }

    private Optional<String> getMessage(String name, FormField field) {
        String result = messages.get(field.name() + "." + name);
        if (result == null) {
            result = messages.get(name);
        }
        return Optional.ofNullable(result);
    }

    private static Map<String, String> loadMessages(ProcessKey processKey, String formName, ProcessStateManager stateManager) {
        Function<InputStream, Optional<Map<String, String>>> converter = inputStream -> {
            try {
                Map<String, String> result = new HashMap<>();
                Properties p = new Properties();
                p.load(inputStream);
                for (final String name : p.stringPropertyNames()) {
                    result.put(name, p.getProperty(name));
                }
                return Optional.of(result);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        String globalPath = Constants.Files.ERROR_MESSAGES_FILE_NAME;
        String formSpecificPath = "forms/" + formName + "/" + Constants.Files.ERROR_MESSAGES_FILE_NAME;

        return Stream.of(formSpecificPath, globalPath)
                .map(p -> stateManager.get(processKey, p, converter).orElse(null))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(Collections.emptyMap());
    }
}
