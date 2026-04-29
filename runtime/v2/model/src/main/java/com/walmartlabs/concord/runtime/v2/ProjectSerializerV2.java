package com.walmartlabs.concord.runtime.v2;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.parser.SimpleOptions;
import com.walmartlabs.concord.runtime.v2.serializer.*;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;

public class ProjectSerializerV2 {

    private final ObjectMapper objectMapper;

    public ProjectSerializerV2() {
        this.objectMapper = createObjectMapper();
    }

    public void write(ProcessDefinition processDefinition, OutputStream out) throws IOException {
        this.objectMapper.writeValue(out, processDefinition);
    }

    public String toString(ProcessDefinition processDefinition) throws Exception {
        return objectMapper.writeValueAsString(processDefinition);
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    private static ObjectMapper createObjectMapper() {
        SimpleModule module = new SimpleModule()
                .addSerializer(SimpleOptions.class, new SimpleOptionsSerializer())
                .addSerializer(Retry.class, new RetryOptionsSerializer())
                .addSerializer(Loop.class, new LoopOptionsSerializer())
                .addSerializer(WithItems.class, new WithItemsSerializer())
                .addSerializer(Checkpoint.class, new CheckpointStepSerializer())
                .addSerializer(ExitStep.class, new ExitStepSerializer())
                .addSerializer(Expression.class, new ExpressionStepSerializer())
                .addSerializer(FlowCall.class, new FlowCallStepSerializer())
                .addSerializer(FormCall.class, new FormCallStepSerializer())
                .addSerializer(FormField.class, new FormFieldSerializer())
                .addSerializer(GroupOfSteps.class, new GroupOfStepsSerializer())
                .addSerializer(IfStep.class, new IfStepSerializer())
                .addSerializer(ParallelBlock.class, new ParallelBlockSerializer())
                .addSerializer(ReturnStep.class, new ReturnStepSerializer())
                .addSerializer(ScriptCall.class, new ScriptCallStepSerializer())
                .addSerializer(SetVariablesStep.class, new SetVariablesStepSerializer())
                .addSerializer(SuspendStep.class, new SuspendStepSerializer())
                .addSerializer(SwitchStep.class, new SwitchStepSerializer())
                .addSerializer(TaskCall.class, new TaskCallStepSerializer())
                .addSerializer(Form.class, new FormDefinitionSerializer())
                .addSerializer(Trigger.class, new TriggerSerializer())
                .addSerializer(ProcessDefinition.class, new ProcessDefinitionSerializer())
                .addSerializer(Duration.class, new DurationSerializer())
                .addSerializer(Flow.class, new FlowSerializer());

        return new ObjectMapper(new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
                .registerModule(module);
    }
}
