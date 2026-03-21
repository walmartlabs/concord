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
import com.walmartlabs.concord.forms.FormFields.FileField;
import com.walmartlabs.concord.forms.FormUtils;
import com.walmartlabs.concord.forms.ValidationError;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.forms.Constants.FORM_FILES;
import static org.fusesource.jansi.Ansi.ansi;

final class LocalFormPrompts {

    private static final DefaultFormValidatorLocale LOCALE = new DefaultFormValidatorLocale();
    private static final String HIDDEN_VALUE = "<hidden>";

    private final Path workDir;
    private final boolean printHeader;
    private final DefaultFormValidator validator;
    private final PromptIo promptIo;

    LocalFormPrompts(Path workDir) {
        this(workDir, true);
    }

    LocalFormPrompts(Path workDir, boolean printHeader) {
        this.workDir = workDir;
        this.printHeader = printHeader;
        this.validator = new DefaultFormValidator(LOCALE);
        this.promptIo = new PromptIo();
    }

    Map<String, Object> prompt(Form form) throws Exception {
        var rawValues = new LinkedHashMap<String, Object>();

        while (true) {
            if (printHeader) {
                printHeader(form);
            }
            collectRawValues(form, rawValues);

            Map<String, Object> converted = null;
            try {
                converted = convert(form, rawValues);
                var errors = validator.validate(form, converted);
                if (!errors.isEmpty()) {
                    cleanupTempFormFiles(converted);
                    clearSensitiveValues(form, rawValues);
                    printValidationErrors(errors);
                    continue;
                }

                moveFormFiles(converted);

                var values = new LinkedHashMap<>(converted);
                values.remove(FORM_FILES);

                var payload = new LinkedHashMap<String, Object>();
                payload.put(form.name(), values);
                return payload;
            } catch (FormUtils.ValidationException e) {
                cleanupTempFormFiles(converted);
                clearSensitiveValues(form, rawValues);
                printError(e.getMessage());
            }
        }
    }

    private void printHeader(Form form) {
        System.out.println("Pending form input:");
        System.out.println("  " + form.name() + " -> " + form.eventName());
        if (form.options().isYield()) {
            printWarning("'yield' is informational only in local CLI.");
        }
        if (form.options().saveSubmittedBy()) {
            printWarning("'saveSubmittedBy' is ignored in local CLI.");
        }
        if (!form.options().runAs().isEmpty()) {
            printWarning("'runAs' restrictions are not enforced in local CLI.");
        }
    }

    private void collectRawValues(Form form, Map<String, Object> rawValues) {
        for (var field : form.fields()) {
            if (Boolean.TRUE.equals(field.getOption(FormFields.CommonFieldOptions.READ_ONLY))) {
                continue;
            }

            var value = promptField(field, rawValues.get(field.name()));
            if (value == MissingValue.INSTANCE) {
                rawValues.remove(field.name());
            } else {
                rawValues.put(field.name(), value);
            }
        }
    }

    private Object promptField(FormField field, Object currentValue) {
        if (isRepeated(field)) {
            return promptRepeatedField(field, currentValue);
        }

        while (true) {
            var line = readValue(field, currentValue);
            if (line == null) {
                return currentValue != null ? currentValue : MissingValue.INSTANCE;
            }

            if (FileField.TYPE.equals(field.type())) {
                var path = Path.of(line);
                if (!Files.isRegularFile(path)) {
                    printError("File not found: " + path);
                    continue;
                }
                return path;
            }

            return normalizeRawValue(field, line);
        }
    }

    private Object promptRepeatedField(FormField field, Object currentValue) {
        if (currentValue instanceof Collection && !((Collection<?>) currentValue).isEmpty()) {
            var current = isPasswordField(field) ? HIDDEN_VALUE : currentValue;
            System.out.println("Current values for " + label(field) + ": " + current);
        }

        var values = new ArrayList<>();
        while (true) {
            var line = readValue(field, null, values.size() + 1);
            if (line == null) {
                if (values.isEmpty()) {
                    return currentValue != null ? currentValue : MissingValue.INSTANCE;
                }
                return values;
            }

            if (FileField.TYPE.equals(field.type())) {
                var path = Path.of(line);
                if (!Files.isRegularFile(path)) {
                    printError("File not found: " + path);
                    continue;
                }
                values.add(path);
            } else {
                values.add(normalizeRawValue(field, line));
            }
        }
    }

    private String readValue(FormField field, Object currentValue) {
        return readValue(field, currentValue, null);
    }

    private String readValue(FormField field, Object currentValue, Integer idx) {
        var prompt = buildPrompt(field, currentValue, idx);
        var password = "password".equals(field.getOption(FormFields.StringField.INPUT_TYPE));
        var line = password ? promptIo.readPassword(prompt) : promptIo.readLine(prompt);
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        return line;
    }

    private Map<String, Object> convert(Form form, Map<String, Object> rawValues) throws Exception {
        var convertedInput = new LinkedHashMap<String, Object>();
        var opened = new ArrayList<InputStream>();

        try {
            for (var field : form.fields()) {
                var value = rawValues.get(field.name());
                if (value == null) {
                    continue;
                }

                convertedInput.put(field.name(), toFormInput(value, field, opened));
            }
            return new LinkedHashMap<>(FormUtils.convert(LOCALE, form, convertedInput));
        } finally {
            for (var in : opened) {
                try {
                    in.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    private Object toFormInput(Object value, FormField field, List<InputStream> opened) throws IOException {
        if (!FileField.TYPE.equals(field.type())) {
            return value;
        }

        if (value instanceof Path) {
            var in = Files.newInputStream((Path) value);
            opened.add(in);
            return in;
        }

        if (value instanceof Collection) {
            var result = new ArrayList<>();
            for (var item : (Collection<?>) value) {
                result.add(toFormInput(item, field, opened));
            }
            return result;
        }

        return value;
    }

    @SuppressWarnings("unchecked")
    private void moveFormFiles(Map<String, Object> converted) throws IOException {
        var formFiles = (Map<String, String>) converted.get(FORM_FILES);
        if (formFiles == null || formFiles.isEmpty()) {
            return;
        }

        for (var e : formFiles.entrySet()) {
            var destination = workDir.resolve(e.getKey());
            var parent = destination.getParent();
            if (parent != null && Files.notExists(parent)) {
                Files.createDirectories(parent);
            }
            Files.move(Path.of(e.getValue()), destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @SuppressWarnings("unchecked")
    private static void cleanupTempFormFiles(Map<String, Object> converted) {
        if (converted == null) {
            return;
        }

        var formFiles = (Map<String, String>) converted.get(FORM_FILES);
        if (formFiles == null) {
            return;
        }

        for (var tmpFile : formFiles.values()) {
            try {
                Files.deleteIfExists(Path.of(tmpFile));
            } catch (IOException ignored) {
                // best effort cleanup for validation retries
            }
        }
    }

    private static void printValidationErrors(List<ValidationError> errors) {
        for (var error : errors) {
            printError(error.error());
        }
    }

    private static void printError(String message) {
        System.err.println(ansi().fgBrightRed().a("Error: ").a(message).reset());
    }

    private static void printWarning(String message) {
        System.out.println(ansi().fgBrightYellow().a("Warning: ").a(message).reset());
    }

    private static String buildPrompt(FormField field, Object currentValue, Integer idx) {
        var details = new ArrayList<String>();
        details.add(field.type());
        details.add(isOptional(field) ? "optional" : "required");

        if (field.allowedValue() != null) {
            details.add("allowed: " + field.allowedValue());
        }

        if (currentValue != null) {
            details.add("current: " + promptValue(field, currentValue));
        } else if (field.defaultValue() != null) {
            details.add("default: " + promptValue(field, field.defaultValue()));
        }

        if (FormFields.DateField.TYPE.equals(field.type()) || FormFields.DateTimeField.TYPE.equals(field.type())) {
            details.add("format: ISO-8601");
        }

        var name = idx != null ? label(field) + " [" + idx + "]" : label(field);
        return name + " [" + String.join(", ", details) + "]: ";
    }

    private static String promptValue(FormField field, Object value) {
        return isPasswordField(field) ? HIDDEN_VALUE : String.valueOf(value);
    }

    private static boolean isPasswordField(FormField field) {
        return "password".equals(field.getOption(FormFields.StringField.INPUT_TYPE));
    }

    private static void clearSensitiveValues(Form form, Map<String, Object> rawValues) {
        for (var field : form.fields()) {
            if (isPasswordField(field)) {
                rawValues.remove(field.name());
            }
        }
    }

    private static boolean isRepeated(FormField field) {
        return field.cardinality() == FormField.Cardinality.ANY || field.cardinality() == FormField.Cardinality.AT_LEAST_ONE;
    }

    private static boolean isOptional(FormField field) {
        return field.cardinality() == FormField.Cardinality.ONE_OR_NONE || field.cardinality() == FormField.Cardinality.ANY;
    }

    private static Object normalizeRawValue(FormField field, String line) {
        if (FormFields.BooleanField.TYPE.equals(field.type())) {
            var normalized = line.trim().toLowerCase();
            if ("y".equals(normalized) || "yes".equals(normalized)) {
                return "true";
            }
            if ("n".equals(normalized) || "no".equals(normalized)) {
                return "false";
            }
            return normalized;
        }

        if (!FormFields.StringField.TYPE.equals(field.type())) {
            return line.trim();
        }

        return line;
    }

    private static String label(FormField field) {
        return field.label() != null ? field.label() : field.name();
    }

    private enum MissingValue {
        INSTANCE
    }

    private static final class PromptIo {

        private final Console console = System.console();
        private final BufferedReader reader = console == null
                ? new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
                : null;

        String readLine(String prompt) {
            try {
                if (console != null) {
                    return console.readLine("%s", prompt);
                }

                System.out.print(prompt);
                System.out.flush();
                var line = reader.readLine();
                if (line == null) {
                    throw new IllegalStateException("End of input while reading form values");
                }
                return line;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String readPassword(String prompt) {
            if (console != null) {
                var chars = console.readPassword("%s", prompt);
                return chars != null ? new String(chars) : "";
            }

            return readLine(prompt);
        }
    }
}
