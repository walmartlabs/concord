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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.walmartlabs.concord.forms.Form;
import com.walmartlabs.concord.forms.FormField;
import com.walmartlabs.concord.forms.FormFields;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class LocalSuspendPrinter {

    private static final ObjectMapper YAML_OBJECT_MAPPER = new ObjectMapper(new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

    static void printSuspendGuidance(Path resumeDir,
                                     Set<String> events,
                                     Collection<Form> pendingForms,
                                     boolean interactiveAvailable) {

        System.out.println("Process suspended.");
        System.out.println();
        printResumeContext(resumeDir, System.out);
        System.out.println();
        printPendingForms(pendingForms);
        printNonInteractiveSupport(pendingForms, interactiveAvailable, System.out);
        printAdditionalEvents(additionalEvents(events, pendingForms));
        printContinueWith(pendingForms, additionalEvents(events, pendingForms), interactiveAvailable, false);
    }

    static void printInputRequired(Path resumeDir,
                                   Set<String> events,
                                   Collection<Form> pendingForms,
                                   boolean interactiveAvailable) {

        printResumeContext(resumeDir, System.err);
        System.err.println();
        if (pendingForms.size() == 1) {
            System.err.println("Pending form requires input in non-interactive mode.");
        } else {
            System.err.println("Pending forms require input or explicit event selection.");
        }
        System.err.println();
        printPendingForms(pendingForms, System.err);
        printNonInteractiveSupport(pendingForms, interactiveAvailable, System.err);
        printAdditionalEvents(additionalEvents(events, pendingForms), System.err);
        printContinueWith(pendingForms, additionalEvents(events, pendingForms), interactiveAvailable, true);
    }

    static void printDescribeSelectionRequired(Path resumeDir,
                                               Set<String> events,
                                               Collection<Form> pendingForms) {

        printResumeContext(resumeDir, System.err);
        System.err.println();
        System.err.println("Pending forms require explicit event selection before describing input.");
        System.err.println();
        printPendingForms(pendingForms, System.err);
        printAdditionalEvents(additionalEvents(events, pendingForms), System.err);
        System.err.println("Continue with:");
        System.err.println("  Describe input:");
        for (var form : pendingForms) {
            System.err.println("    " + describeInputCommand(form));
        }
    }

    static void printEventSelectionRequired(Path resumeDir, Set<String> events) {
        printResumeContext(resumeDir, System.err);
        System.err.println();
        System.err.println("Multiple waiting events require explicit event selection.");
        System.err.println();
        printAdditionalEvents(new TreeSet<>(events), System.err);
        System.err.println("Continue with:");
        System.err.println("  Resume event:");
        for (var event : new TreeSet<>(events)) {
            System.err.println("    " + resumeCommand() + " --event " + shellQuote(event));
        }
    }

    static void printDescribeInput(Path resumeDir, Form form) throws Exception {
        printResumeDir(resumeDir);
        System.out.println("Pending form input:");
        System.out.println("  " + formMapping(form));

        var requiredFields = userFields(form).stream()
                .filter(f -> !isOptional(f))
                .toList();
        var optionalFields = userFields(form).stream()
                .filter(LocalSuspendPrinter::isOptional)
                .toList();
        var fileFields = userFields(form).stream()
                .filter(LocalSuspendPrinter::isFileField)
                .toList();

        printFieldList("Required fields:", requiredFields, System.out);
        printFieldList("Optional fields:", optionalFields, System.out);

        if (!fileFields.isEmpty()) {
            System.out.println("File-upload fields:");
            for (var field : fileFields) {
                System.out.println("  " + field.name());
            }
            System.out.println("Non-interactive submission:");
            System.out.println("  not supported for file-upload fields");
        }

        var example = examplePayload(form);
        if (!example.isEmpty()) {
            System.out.println("Example input file:");
            for (var line : YAML_OBJECT_MAPPER.writeValueAsString(example).stripTrailing().split("\\R")) {
                System.out.println("  " + line);
            }
        }
    }

    static void printUnsupportedNonInteractiveForm(Path resumeDir, Form form, boolean interactiveAvailable) {
        printResumeContext(resumeDir, System.err);
        System.err.println();
        System.err.println("Pending form cannot be submitted non-interactively because it contains file-upload fields.");
        System.err.println();
        printPendingForms(List.of(form), System.err);
        System.err.println("Continue with:");
        System.err.println("  Describe input:");
        System.err.println("    " + describeInputCommand(form));
        if (interactiveAvailable) {
            System.err.println("  Fill interactively:");
            System.err.println("    " + resumeCommand());
        }
    }

    static boolean supportsNonInteractiveInput(Form form) {
        return userFields(form).stream().noneMatch(LocalSuspendPrinter::isFileField);
    }

    private static void printContinueWith(Collection<Form> pendingForms,
                                          Set<String> additionalEvents,
                                          boolean interactiveAvailable,
                                          boolean toErr) {

        var out = toErr ? System.err : System.out;
        out.println("Continue with:");

        if (interactiveAvailable && !pendingForms.isEmpty()) {
            out.println("  Fill interactively:");
            out.println("    " + resumeCommand());
        }

        if (!pendingForms.isEmpty()) {
            out.println("  Describe input:");
            for (var form : pendingForms) {
                out.println("    " + describeInputCommand(form));
            }
        }

        if (pendingForms.stream().anyMatch(LocalSuspendPrinter::supportsNonInteractiveInput)) {
            out.println("  Submit input:");
        }
        for (var form : pendingForms) {
            if (!supportsNonInteractiveInput(form)) {
                continue;
            }

            out.println("    " + inputFileCommand(form));
        }

        if (!additionalEvents.isEmpty()) {
            out.println("  Resume event:");
            for (var event : additionalEvents) {
                out.println("    " + resumeCommand() + " --event " + shellQuote(event));
            }
        }
    }

    private static void printResumeContext(Path resumeDir, java.io.PrintStream out) {
        out.println("Resume dir: " + resumeDir.normalize().toAbsolutePath());
        out.println("Commands below assume you are in that directory.");
    }

    private static void printResumeDir(Path resumeDir) {
        System.out.println("Resume dir: " + resumeDir.normalize().toAbsolutePath());
    }

    private static void printPendingForms(Collection<Form> pendingForms) {
        printPendingForms(pendingForms, System.out);
    }

    private static void printPendingForms(Collection<Form> pendingForms, java.io.PrintStream out) {
        if (pendingForms.isEmpty()) {
            return;
        }

        out.println("Pending forms:");
        var formKeyWidth = "Form key".length();
        for (var form : pendingForms) {
            formKeyWidth = Math.max(formKeyWidth, form.name().length());
        }

        out.printf("  %-" + formKeyWidth + "s  %s%n", "Form key", "Event ID");
        for (var form : pendingForms) {
            out.printf("  %-" + formKeyWidth + "s  %s%n", form.name(), form.eventName());
        }
        out.println();
    }

    private static void printAdditionalEvents(Set<String> events) {
        printAdditionalEvents(events, System.out);
    }

    private static void printAdditionalEvents(Set<String> events, java.io.PrintStream out) {
        if (events.isEmpty()) {
            return;
        }

        out.println("Additional waiting events:");
        for (var event : events) {
            out.println("  " + event);
        }
        out.println();
    }

    private static void printFieldList(String header, List<FormField> fields, java.io.PrintStream out) {
        if (fields.isEmpty()) {
            return;
        }

        out.println(header);
        for (var field : fields) {
            out.println("  " + field.name());
        }
    }

    private static void printNonInteractiveSupport(Collection<Form> pendingForms,
                                                   boolean interactiveAvailable,
                                                   java.io.PrintStream out) {
        if (interactiveAvailable || pendingForms.size() != 1) {
            return;
        }

        var form = pendingForms.iterator().next();
        if (supportsNonInteractiveInput(form)) {
            return;
        }

        out.println("Non-interactive submission:");
        out.println("  not supported for file-upload fields");
        out.println();
    }

    private static String formMapping(Form form) {
        return form.name() + " -> " + form.eventName();
    }

    private static Set<String> additionalEvents(Set<String> events, Collection<Form> pendingForms) {
        var result = new TreeSet<>(events);
        result.removeAll(LocalFormState.formEvents(pendingForms));
        return result;
    }

    private static String describeInputCommand(Form form) {
        return resumeCommand() + " --event " + shellQuote(form.eventName()) + " --describe-input";
    }

    private static String inputFileCommand(Form form) {
        return resumeCommand() + " --event " + shellQuote(form.eventName()) + " --input-file " + shellQuote(exampleInputFileName(form));
    }

    private static List<FormField> userFields(Form form) {
        return form.fields().stream()
                .filter(f -> !Boolean.TRUE.equals(f.getOption(FormFields.CommonFieldOptions.READ_ONLY)))
                .toList();
    }

    private static boolean isOptional(FormField field) {
        return field.cardinality() == FormField.Cardinality.ONE_OR_NONE || field.cardinality() == FormField.Cardinality.ANY;
    }

    private static boolean isRepeated(FormField field) {
        return field.cardinality() == FormField.Cardinality.ANY || field.cardinality() == FormField.Cardinality.AT_LEAST_ONE;
    }

    private static boolean isFileField(FormField field) {
        return FormFields.FileField.TYPE.equals(field.type());
    }

    private static String exampleInputFileName(Form form) {
        return form.name().replaceAll("[^A-Za-z0-9._-]+", "_") + ".yml";
    }

    private static Object firstAllowedValue(Serializable value) {
        if (value instanceof List<?> l && !l.isEmpty()) {
            return l.get(0);
        }
        return value;
    }

    private static Map<String, Object> examplePayload(Form form) {
        var values = new LinkedHashMap<String, Object>();
        for (var field : userFields(form)) {
            values.put(field.name(), exampleValue(field));
        }

        if (values.isEmpty()) {
            return Map.of();
        }

        return Map.of(form.name(), values);
    }

    private static Object exampleValue(FormField field) {
        var baseValue = field.defaultValue() != null ? field.defaultValue() : firstAllowedValue(field.allowedValue());
        if (baseValue == null) {
            baseValue = switch (field.type()) {
                case "boolean" -> Boolean.TRUE;
                case "int" -> 0;
                case "decimal" -> 0.0d;
                case "file" -> "path/to/file";
                default -> "";
            };
        }

        if (!isRepeated(field)) {
            return baseValue;
        }

        if (baseValue instanceof Collection<?> c) {
            return new ArrayList<>(c);
        }

        return List.of(baseValue);
    }

    private static String resumeCommand() {
        return "concord resume";
    }

    private static String shellQuote(String value) {
        if (value.matches("[A-Za-z0-9_./:=+-]+")) {
            return value;
        }

        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private LocalSuspendPrinter() {
    }
}
