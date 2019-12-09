package com.walmartlabs.concord.server.org.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.Strings;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.model.Trigger;
import com.walmartlabs.concord.sdk.Constants;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.SourceMap;
import org.sonatype.siesta.ValidationErrorsException;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Pattern;

public class ProjectValidator {

    private static final CronParser parser =
            new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

    public static Result validate(ProjectDefinition pd) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Trigger t : pd.getTriggers()) {
            validateTrigger(pd, t, errors, warnings);
        }

        return new Result(errors, warnings);
    }

    private static void validateTrigger(ProjectDefinition pd, Trigger t, List<String> errors, List<String> warnings) {
        validateEntryPoint(pd, t, errors, warnings);

        validateSpec(t, errors);

        validateTimezone(t, errors);

        if (Objects.isNull(t.getConditions())) {
            return;
        }

        t.getConditions().entrySet().stream()
                .filter(v -> v.getValue() instanceof String)
                .filter(v -> !Constants.Trigger.CRON_SPEC.equals(v.getKey()))
                .forEach(v -> validateRegex(t, errors, v));
    }

    private static void validateEntryPoint(ProjectDefinition pd, Trigger t, List<String> errors, List<String> warnings) {
        if (Strings.isNullOrEmpty(t.getEntryPoint())) {
            errors.add(makeErrorMessage(t, Constants.Request.ENTRY_POINT_KEY, "is missing"));
            return;
        }

        Map<String, ProcessDefinition> flows = pd.getFlows();
        if (Objects.isNull(flows) || !flows.containsKey(t.getEntryPoint())) {
            warnings.add(makeErrorMessage(t, Constants.Request.ENTRY_POINT_KEY, "does not point to a valid flow. " +
                    "This warning can be ignored if the entryPoint references a flow from a template."));
        }
    }

    private static void validateSpec(Trigger t, List<String> errors) {
        String triggerName = t.getName();
        if (!triggerName.equals("cron")) {
            return;
        }

        String k = Constants.Trigger.CRON_SPEC;

        if (Objects.isNull(t.getConditions())) {
            errors.add(makeErrorMessage(t, k, "is missing"));
            return;
        }

        Object spec = t.getConditions().get(k);
        if (Objects.isNull(spec)) {
            errors.add(makeErrorMessage(t, k, "is missing"));
            return;
        }

        try {
            parser.parse((String) spec);
        } catch (ValidationErrorsException e) {
            errors.add(makeErrorMessage(t, k, "is not valid: " + e.getMessage()));
        }
    }

    private static void validateTimezone(Trigger t, List<String> errors) {
        String triggerName = t.getName();
        if (!triggerName.equals("cron")) {
            return;
        }

        if (Objects.isNull(t.getConditions())) {
            return;
        }

        Object timezone = t.getConditions().get("timezone");
        if (Objects.isNull(timezone)) {
            return;
        }

        if (!(timezone instanceof String)) {
            errors.add(makeErrorMessage(t, "timezone", "invalid type: string expected"));
            return;
        }

        if (!Arrays.asList(TimeZone.getAvailableIDs()).contains(timezone)) {
            errors.add(makeErrorMessage(t, "timezone", "with value '" + timezone + "' not found"));
        }
    }

    private static void validateRegex(Trigger t, List<String> errors, Map.Entry<String, Object> entry) {
        try {
            Pattern.compile(entry.getValue().toString());
        } catch (Exception e) {
            errors.add(makeErrorMessage(t, entry.getKey(), "is not a valid regular expression: " + e.getMessage()));
        }
    }

    private static String location(SourceMap m) {
        return "@ src: " + m.getSource() + ", line: " + m.getLine() + ", col: " + m.getColumn();
    }

    private static String makeErrorMessage(Trigger t, String property, String message) {
        return "trigger: " + t.getName() + " -> " + property + " " + (message) + " " + location(t.getSourceMap());
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Result implements Serializable {

        private static final long serialVersionUID = 1L;

        private final List<String> errors;
        private final List<String> warnings;

        public Result(List<String> errors, List<String> warnings) {
            this.errors = errors;
            this.warnings = warnings;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public boolean isValid() {
            return errors == null || errors.isEmpty();
        }
    }
}

