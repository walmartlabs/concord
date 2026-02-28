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
import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.model.TaskCallValidation.ValidationMode;
import io.takari.parc.Parser;

import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;

/**
 * Grammar for the {@code configuration} section of Concord YAML files.
 */
public final class ConfigurationGrammar {

    private static final Parser<Atom, ExclusiveMode> exclusive =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(ImmutableExclusiveMode::builder,
                            o -> options(
                                    mandatory("group", stringNotEmptyVal.map(o::group)),
                                    optional("mode", enumVal(ExclusiveMode.Mode.class).map(o::mode))))
                            .map(ImmutableExclusiveMode.Builder::build));

    public static final Parser<Atom, ExclusiveMode> exclusiveVal =
            orError(exclusive, YamlValueType.EXCLUSIVE_MODE);

    private static final Parser<Atom, EventConfiguration> events =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(ImmutableEventConfiguration::builder,
                            o -> options(
                                    optional("batchFlushInterval", intVal.map(o::batchFlushInterval)),
                                    optional("batchSize", intVal.map(o::batchSize)),
                                    optional("recordEvents", booleanVal.map(o::recordEvents)),
                                    optional("recordTaskInVars", booleanVal.map(o::recordTaskInVars)),
                                    optional("truncateInVars", booleanVal.map(o::truncateInVars)),
                                    optional("truncateMaxStringLength", intVal.map(o::truncateMaxStringLength)),
                                    optional("truncateMaxArrayLength", intVal.map(o::truncateMaxArrayLength)),
                                    optional("truncateMaxDepth", intVal.map(o::truncateMaxDepth)),
                                    optional("recordTaskOutVars", booleanVal.map(o::recordTaskOutVars)),
                                    optional("truncateOutVars", booleanVal.map(o::truncateOutVars)),
                                    optional("updateMetaOnAllEvents", booleanVal.map(o::updateMetaOnAllEvents)),
                                    optional("inVarsBlacklist", stringArrayVal.map(o::inVarsBlacklist)),
                                    optional("outVarsBlacklist", stringArrayVal.map(o::outVarsBlacklist)),
                                    optional("recordTaskMeta", booleanVal.map(o::recordTaskMeta)),
                                    optional("truncateMeta", booleanVal.map(o::truncateMeta)),
                                    optional("metaBlacklist", stringArrayVal.map(o::metaBlacklist))))
                            .map(ImmutableEventConfiguration.Builder::build));

    private static final Parser<Atom, EventConfiguration> eventsVal =
            orError(events, YamlValueType.EVENTS_CFG);

    // Case-insensitive enum parsing for ValidationMode
    private static final Parser<Atom, ValidationMode> validationModeVal =
            orError(stringVal.map(s -> ValidationMode.valueOf(s.toUpperCase())), YamlValueType.VALIDATION_MODE);

    // Mutable holder for building TaskCallValidation
    private static final class TaskCallValidationHolder {
        ValidationMode in = ValidationMode.DISABLED;
        ValidationMode out = ValidationMode.DISABLED;

        TaskCallValidationHolder in(ValidationMode v) {
            this.in = v;
            return this;
        }

        TaskCallValidationHolder out(ValidationMode v) {
            this.out = v;
            return this;
        }

        TaskCallValidation build() {
            return new TaskCallValidation(in, out);
        }
    }

    private static final Parser<Atom, TaskCallValidation> taskCallValidation =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(TaskCallValidationHolder::new,
                            o -> options(
                                    optional("in", validationModeVal.map(o::in)),
                                    optional("out", validationModeVal.map(o::out))))
                            .map(TaskCallValidationHolder::build));

    private static final Parser<Atom, TaskCallValidation> taskCallValidationVal =
            orError(taskCallValidation, YamlValueType.TASK_CALL_VALIDATION);

    // Mutable holder for building ValidationConfiguration
    private static final class ValidationConfigurationHolder {
        TaskCallValidation taskCalls = new TaskCallValidation();

        ValidationConfigurationHolder taskCalls(TaskCallValidation v) {
            this.taskCalls = v;
            return this;
        }

        ValidationConfiguration build() {
            return new ValidationConfiguration(taskCalls);
        }
    }

    private static final Parser<Atom, ValidationConfiguration> validation =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(ValidationConfigurationHolder::new,
                            o -> options(
                                    optional("taskCalls", taskCallValidationVal.map(o::taskCalls))))
                            .map(ValidationConfigurationHolder::build));

    private static final Parser<Atom, ValidationConfiguration> validationVal =
            orError(validation, YamlValueType.VALIDATION_CFG);

    private static final Parser<Atom, ProcessDefinitionConfiguration> cfg =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(ImmutableProcessDefinitionConfiguration::builder,
                            o -> options(
                                    optional("runtime", stringVal.map(o::runtime)),
                                    optional("entryPoint", stringVal.map(o::entryPoint)),
                                    optional("dependencies", stringArrayVal.map(o::dependencies)),
                                    optional("extraDependencies", stringArrayVal.map(o::extraDependencies)),
                                    optional("meta", mapVal.map(o::meta)),
                                    optional("requirements", mapVal.map(o::requirements)),
                                    optional("processTimeout", durationVal.map(o::processTimeout)),
                                    optional("suspendTimeout", durationVal.map(o::suspendTimeout)),
                                    optional("activeProfiles", stringArrayVal.map(o::activeProfiles)),
                                    optional("exclusive", exclusiveVal.map(o::exclusive)),
                                    optional("events", eventsVal.map(o::events)),
                                    optional("out", stringArrayVal.map(o::addAllOut)),
                                    optional("arguments", mapVal.map(o::arguments)),
                                    optional("debug", booleanVal.map(o::debug)),
                                    optional("template", stringVal.map(o::template)),
                                    optional("parallelLoopParallelism", intVal.map(o::parallelLoopParallelism)),
                                    optional("validation", validationVal.map(o::validation))))
                            .map(ImmutableProcessDefinitionConfiguration.Builder::build));

    public static final Parser<Atom, ProcessDefinitionConfiguration> processCfgVal =
            orError(cfg, YamlValueType.PROCESS_CFG);

    private ConfigurationGrammar() {
    }
}
