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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaInject;
import com.walmartlabs.concord.runtime.v2.model.Loop;

@JsonTypeName("Loop")
public interface LoopMixIn {

    @JsonProperty("items")
    @JsonSchemaInject(json = "{\"oneOf\": [ {\"type\": \"string\"}, {\"type\": \"object\"}, {\"type\": \"array\"} ]}", merge = false)
    Object items();

    @JsonProperty("mode")
    @JsonSchemaInject(json = "{\"enum\" : [\"serial\", \"parallel\"]}")
    Loop.Mode mode();

    @JsonProperty("parallelism")
    Integer parallelism();
}
