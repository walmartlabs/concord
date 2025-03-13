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

import com.fasterxml.jackson.annotation.*;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaInject;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaString;
import com.walmartlabs.concord.runtime.v2.model.ExclusiveMode;
import com.walmartlabs.concord.runtime.v2.model.GithubTriggerExclusiveMode;
import com.walmartlabs.concord.runtime.v2.model.Trigger;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TriggerMixIn.ManualTriggerMixIn.class, name = "Manual Trigger"),
        @JsonSubTypes.Type(value = TriggerMixIn.CronTriggerMixIn.class, name = "Cron Trigger"),
        @JsonSubTypes.Type(value = TriggerMixIn.GithubTriggerMixIn.class, name = "Github Trigger"),
        @JsonSubTypes.Type(value = TriggerMixIn.OneOpsTriggerMixIn.class, name = "OneOps Trigger"),
        @JsonSubTypes.Type(value = TriggerMixIn.GenericTriggerMixIn.class, name = "Generic Trigger")
})
@SuppressWarnings("unused")
public interface TriggerMixIn extends Trigger {

    @JsonTypeName("ManualTrigger")
    interface ManualTriggerMixIn extends TriggerMixIn {

        @JsonProperty("manual")
        ManualTriggerParams params();

        interface ManualTriggerParams extends DefaultTriggerParams {

            @JsonProperty("name")
            String name();

            @JsonProperty("exclusive")
            ExclusiveMode exclusive();
        }
    }

    @JsonTypeName("CronTrigger")
    interface CronTriggerMixIn extends TriggerMixIn {

        @JsonProperty("cron")
        CronTriggerMixIn.CronTriggerParams params();

        interface CronTriggerParams extends DefaultTriggerParams {

            @JsonProperty("exclusive")
            ExclusiveMode exclusive();

            @JsonProperty(value = "spec", required = true)
            String spec();

            @JsonProperty("timezone")
            String timezone();

            @JsonProperty("runAs")
            RunAs runAs();
        }

        interface RunAs {

            @JsonProperty(value = "withSecret", required = true)
            String withSecret();
        }
    }

    @JsonTypeName("GithubTrigger")
    interface GithubTriggerMixIn extends TriggerMixIn {

        @JsonProperty("github")
        GithubTriggerMixIn.GithubTriggerParams params();

        interface GithubTriggerParams extends DefaultTriggerParams {

            @JsonSchemaInject(json = "{\"enum\" : [2]}")
            @JsonProperty(value = "version", required = true)
            int version();

            @JsonProperty("useInitiator")
            Boolean useInitiator();

            @JsonProperty("useEventCommitId")
            Boolean useEventCommitId();

            @JsonProperty("ignoreEmptyPush")
            Boolean ignoreEmptyPush();

            @JsonProperty("conditions")
            GithubTriggerConditions conditions();

            @JsonProperty("exclusive")
            GithubTriggerExclusiveMode exclusive();

            interface GithubTriggerConditions {
                @JsonProperty(value = "type", required = true)
                String type();

                @JsonProperty("githubOrg")
                String githubOrg();

                @JsonProperty("githubRepo")
                String githubRepo();

                @JsonProperty("githubHost")
                String githubHost();

                @JsonProperty("branch")
                String branch();

                @JsonProperty("sender")
                String sender();

                @JsonProperty("status")
                String status();

                @JsonProperty("repositoryInfo")
                List<Map<String, Object>> repositoryInfo();

                @JsonProperty("payload")
                Map<String, Object> payload();

                @JsonProperty("queryParams")
                Map<String, Object> queryParams();
            }
        }
    }

    @JsonTypeName("OneOpsTrigger")
    interface OneOpsTriggerMixIn extends TriggerMixIn {

        @JsonProperty("oneops")
        Map<String, Object> params();
    }

    @JsonTypeName("GenericTrigger")
    @JsonSchemaInject(strings = {@JsonSchemaString(path = "patternProperties/^(?!(manual|cron|github)$).*$/$ref", value = "#/definitions/GenericTriggerParams")})
    interface GenericTriggerMixIn extends TriggerMixIn {

        @JsonProperty("removeMe")
        GenericTriggerParams params();

        interface GenericTriggerParams extends DefaultTriggerParams {

            @JsonSchemaInject(json = "{\"enum\" : [2]}")
            @JsonProperty(value = "version", required = true)
            int version();

            @JsonProperty("conditions")
            Map<String, Object> conditions();

            @JsonProperty("exclusive")
            ExclusiveMode exclusive();
        }
    }

    interface DefaultTriggerParams {

        @JsonProperty(value = "entryPoint", required = true)
        String entryPoint();

        @JsonProperty("activeProfiles")
        List<String> activeProfiles();

        @JsonProperty("arguments")
        Map<String, Object> arguments();
    }
}
