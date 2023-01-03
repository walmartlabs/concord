package com.walmartlabs.concord.runtime.v2.schema;

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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaInject;

import java.util.List;
import java.util.Map;

public interface GroupOfStepsMixIn extends NamedStep {

    List<StepMixIn> steps();

    @JsonProperty("out")
    @JsonSchemaInject(json = "{\"oneOf\": [ {\"type\": \"array\", \"items\" : {\"type\" : \"string\"}}, {\"type\": \"string\"} ]}", merge = false)
    Object out();

    @JsonProperty("loop")
    LoopMixIn loop();

    @JsonProperty("error")
    List<StepMixIn> errorSteps();

    @JsonProperty("meta")
    Map<String, Object> meta();
}
