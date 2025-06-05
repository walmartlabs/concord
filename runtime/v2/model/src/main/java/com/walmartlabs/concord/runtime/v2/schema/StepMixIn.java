package com.walmartlabs.concord.runtime.v2.schema;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.walmartlabs.concord.runtime.model.Location;
import com.walmartlabs.concord.runtime.v2.model.Step;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TaskCallMixIn.class, name = "Task call"),
        @JsonSubTypes.Type(value = ReturnStepMixIn.class, name = "return"),
        @JsonSubTypes.Type(value = ExitStepMixIn.class, name = "exit"),
        @JsonSubTypes.Type(value = CheckpointStepMixIn.class, name = "Checkpoint"),
        @JsonSubTypes.Type(value = ExpressionShortMixIn.class, name = "Expression (short form)"),
        @JsonSubTypes.Type(value = ExpressionFullMixIn.class, name = "Expression (full form)"),
        @JsonSubTypes.Type(value = LogStepMixIn.class, name = "Log"),
        @JsonSubTypes.Type(value = LogYamlStepMixIn.class, name = "LogYaml"),
        @JsonSubTypes.Type(value = FlowCallStepMixIn.class, name = "Flow call"),
        @JsonSubTypes.Type(value = FormCallStepMixIn.class, name = "Form call"),
        @JsonSubTypes.Type(value = IfStepMixIn.class, name = "IF step"),
        @JsonSubTypes.Type(value = ParallelStepMixIn.class, name = "Parallel step"),
        @JsonSubTypes.Type(value = ScriptCallMixIn.class, name = "Script call"),
        @JsonSubTypes.Type(value = SetStepMixIn.class, name = "Set step"),
        @JsonSubTypes.Type(value = SuspendStepMixIn.class, name = "Suspend step"),
        @JsonSubTypes.Type(value = SwitchStepMixIn.class, name = "Suspend step"),
        @JsonSubTypes.Type(value = ThrowStepMixIn.class, name = "Throw step"),
        @JsonSubTypes.Type(value = TryStepMixIn.class, name = "Try step"),
        @JsonSubTypes.Type(value = BlockStepMixIn.class, name = "Block step")
})
public interface StepMixIn extends Step {

    @Override
    @JsonIgnore
    Location getLocation();
}
