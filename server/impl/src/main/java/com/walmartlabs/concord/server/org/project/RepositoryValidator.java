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
import com.google.common.base.Strings;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.model.Trigger;
import io.takari.bpm.model.ProcessDefinition;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Named
public class RepositoryValidator {

    private static final CronParser parser =
            new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

    public void validate(ProjectDefinition projectDefinition) {
        List<String> errors = new ArrayList<>();
        for (Trigger trigger : projectDefinition.getTriggers()) {
            validateTrigger(errors, trigger, projectDefinition);
        }

        if (!errors.isEmpty()) {
            throw new ValidationErrorsException(String.join("\n", errors));
        }
    }

    private static void validateTrigger(List<String> errors, Trigger trigger, ProjectDefinition projectDefinition) {
        validateEntryPoint(errors, trigger, projectDefinition.getFlows());

        validateSpec(errors, trigger);

        if (Objects.isNull(trigger.getParams())) {
            return;
        }

        trigger.getParams().entrySet().stream()
                .filter(v -> v.getValue() instanceof String)
                .forEach(v -> validateRegex(errors, v, trigger.getName()));
    }

    private static void validateEntryPoint(List<String> errors, Trigger trigger, Map<String, ProcessDefinition> flows) {
        String triggerName = trigger.getName();

        if (Strings.isNullOrEmpty(trigger.getEntryPoint())) {
            errors.add(makeErrorMessage(triggerName, "entryPoint", "is missing"));
            return;
        }

        if (Objects.isNull(flows) || !flows.containsKey(trigger.getEntryPoint())) {
            errors.add(makeErrorMessage(triggerName, "entryPoint", "does not point to valid flow"));
        }
    }

    private static void validateSpec(List<String> errors, Trigger trigger) {
        String triggerName = trigger.getName();
        String specParamKey = "spec";

        if (!triggerName.equals("cron")) {
            return;
        }

        if (Objects.isNull(trigger.getParams())) {
            errors.add(makeErrorMessage(triggerName, specParamKey, "is missing"));
            return;
        }

        Object spec = trigger.getParams().get(specParamKey);
        if (Objects.isNull(spec)) {
            errors.add(makeErrorMessage(triggerName, specParamKey, "is missing"));
            return;
        }

        try {
            parser.parse((String) spec);
        } catch (ValidationErrorsException e) {
            errors.add(makeErrorMessage(triggerName, specParamKey, "is not valid: " + e.getMessage()));
        }
    }

    private static void validateRegex(List<String> errors, Map.Entry<String, Object> entry, String triggerName) {
        try {
            Pattern.compile(entry.getValue().toString());
        } catch (Exception e) {
            errors.add(makeErrorMessage(triggerName, entry.getKey(), "is not a valid regular expression: " + e.getMessage()));
        }
    }

    private static String makeErrorMessage(String triggerName, String property, String message) {
        return triggerName + " -> " + property + " " + (message);
    }
}

