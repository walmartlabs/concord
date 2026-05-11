package com.walmartlabs.concord.cli;

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

import com.walmartlabs.concord.forms.DefaultFormValidator;
import com.walmartlabs.concord.forms.DefaultFormValidatorLocale;
import com.walmartlabs.concord.forms.Form;
import com.walmartlabs.concord.forms.FormField;
import com.walmartlabs.concord.forms.FormFields;
import com.walmartlabs.concord.forms.FormUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.forms.Constants.FORM_FILES;

final class LocalFormInputs {

    private static final DefaultFormValidatorLocale LOCALE = new DefaultFormValidatorLocale();
    private static final DefaultFormValidator VALIDATOR = new DefaultFormValidator(LOCALE);

    static Converted convertAndValidate(Form form, Map<String, Object> rawValues) throws InputException {
        return convertAndValidate(form, rawValues, false);
    }

    static Converted convertAndValidate(Form form, Map<String, Object> input, boolean unwrapFormName) throws InputException {
        var rawValues = unwrapFormName ? unwrapFormValues(form, input) : input;
        var tmpFiles = new LinkedHashMap<String, String>();
        var opened = new ArrayList<InputStream>();

        try {
            var convertedInput = prepareInput(form, rawValues, opened);
            var converted = new LinkedHashMap<>(FormUtils.convert(LOCALE, form, convertedInput, tmpFiles));
            var errors = VALIDATOR.validate(form, converted);
            if (!errors.isEmpty()) {
                cleanupTempFiles(tmpFiles);
                throw new InputException(errors.stream().map(e -> e.error()).toList());
            }

            return new Converted(converted, tmpFiles);
        } catch (FormUtils.ValidationException e) {
            cleanupTempFiles(tmpFiles);
            throw new InputException(List.of(e.getMessage()), e);
        } catch (IOException e) {
            cleanupTempFiles(tmpFiles);
            throw new InputException(List.of(e.getMessage()), e);
        } finally {
            close(opened);
        }
    }

    private static Map<String, Object> prepareInput(Form form, Map<String, Object> rawValues, List<InputStream> opened) throws IOException {
        var convertedInput = new LinkedHashMap<String, Object>();
        for (var field : form.fields()) {
            var value = rawValues.get(field.name());
            if (value == null) {
                continue;
            }

            convertedInput.put(field.name(), toFormInput(value, field, opened));
        }
        return convertedInput;
    }

    private static Object toFormInput(Object value, FormField field, List<InputStream> opened) throws IOException {
        if (!FormFields.FileField.TYPE.equals(field.type())) {
            return value;
        }

        if (value instanceof Path path) {
            var in = Files.newInputStream(path);
            opened.add(in);
            return in;
        }

        if (value instanceof Collection<?> values) {
            var result = new ArrayList<>();
            for (var item : values) {
                result.add(toFormInput(item, field, opened));
            }
            return result;
        }

        return value;
    }

    private static Map<String, Object> unwrapFormValues(Form form, Map<String, Object> input) throws InputException {
        var value = input.get(form.name());
        if (value == null) {
            return input;
        }

        if (!(value instanceof Map<?, ?> values)) {
            throw new InputException(List.of("Expected an object value for form '" + form.name() + "'"));
        }

        var result = new LinkedHashMap<String, Object>();
        for (var e : values.entrySet()) {
            if (!(e.getKey() instanceof String key)) {
                throw new InputException(List.of("Expected string field names for form '" + form.name() + "'"));
            }
            result.put(key, e.getValue());
        }
        return result;
    }

    static void cleanupTempFiles(Map<String, String> tmpFiles) {
        for (var tmpFile : tmpFiles.values()) {
            try {
                Files.deleteIfExists(Path.of(tmpFile));
            } catch (IOException ignored) {
                // best effort cleanup for validation retries
            }
        }
    }

    private static void close(List<InputStream> opened) {
        for (var in : opened) {
            try {
                in.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    record Converted(Map<String, Object> values, Map<String, String> tmpFiles) {

        Map<String, Object> payload(Form form) {
            var values = new LinkedHashMap<>(this.values);
            values.remove(FORM_FILES);

            var payload = new LinkedHashMap<String, Object>();
            payload.put(form.name(), values);
            return payload;
        }

        void cleanupTempFiles() {
            LocalFormInputs.cleanupTempFiles(tmpFiles);
        }
    }

    static final class InputException extends Exception {

        private final List<String> messages;

        private InputException(List<String> messages) {
            this(messages, null);
        }

        private InputException(List<String> messages, Throwable cause) {
            super(String.join(", ", messages), cause);
            this.messages = List.copyOf(messages);
        }

        List<String> messages() {
            return messages;
        }
    }

    private LocalFormInputs() {
    }
}
