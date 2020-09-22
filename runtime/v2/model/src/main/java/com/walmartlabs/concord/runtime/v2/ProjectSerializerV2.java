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
        SimpleModule module = new SimpleModule();
        module.addSerializer(SimpleOptions.class, new SimpleOptionsSerializer());
        module.addSerializer(Retry.class, new RetryOptionsSerializer());
        module.addSerializer(WithItems.class, new WithItemsSerializer());

        module.addSerializer(Checkpoint.class, new CheckpointStepSerializer());
        module.addSerializer(ExitStep.class, new ExitStepSerializer());

        module.addSerializer(Expression.class, new ExpressionStepSerializer());

        module.addSerializer(FlowCall.class, new FlowCallStepSerializer());
        module.addSerializer(FlowCallOptions.class, new FlowCallOptionsSerializer());

        module.addSerializer(FormCall.class, new FormCallStepSerializer());
        module.addSerializer(FormField.class, new FormFieldSerializer());

        module.addSerializer(GroupOfSteps.class, new GroupOfStepsSerializer());
        module.addSerializer(IfStep.class, new IfStepSerializer());
        module.addSerializer(ParallelBlock.class, new ParallelBlockSerializer());
        module.addSerializer(ReturnStep.class, new ReturnStepSerializer());
        module.addSerializer(ScriptCall.class, new ScriptCallStepSerializer());
        module.addSerializer(SetVariablesStep.class, new SetVariablesStepSerializer());
        module.addSerializer(SuspendStep.class, new SuspendStepSerializer());
        module.addSerializer(SwitchStep.class, new SwitchStepSerializer());
        module.addSerializer(TaskCall.class, new TaskCallStepSerializer());

        module.addSerializer(Form.class, new FormDefinitionSerializer());
        module.addSerializer(ProcessConfiguration.class, new ConfigurationSerializer());
        module.addSerializer(Trigger.class, new TriggerSerializer());
        module.addSerializer(ProcessDefinition.class, new ProcessDefinitionSerializer());

        return new ObjectMapper(new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
                .registerModule(module);
    }
}
