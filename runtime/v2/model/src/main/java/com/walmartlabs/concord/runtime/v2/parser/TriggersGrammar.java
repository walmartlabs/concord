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
import com.walmartlabs.concord.runtime.v2.exception.OneOfMandatoryFieldsNotFoundException;
import com.walmartlabs.concord.runtime.v2.exception.UnsupportedException;
import com.walmartlabs.concord.runtime.v2.model.*;
import io.takari.parc.Parser;
import io.takari.parc.Seq;

import java.util.*;
import java.util.function.Supplier;

import static com.walmartlabs.concord.runtime.v2.parser.ConfigurationGrammar.exclusiveVal;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarLookup.lookup;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;
import static io.takari.parc.Combinators.choice;
import static io.takari.parc.Combinators.many1;

public final class TriggersGrammar {

    private static final Parser<Atom, Map<String, Object>> githubTriggerRepositoryInfoItem =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with((Supplier<HashMap<String, Object>>) LinkedHashMap::new,
                            o -> options(
                                    optional("repositoryId", regexpVal.map(v -> o.put("repositoryId", v))),
                                    optional("repository", regexpVal.map(v -> o.put("repository", v))),
                                    optional("projectId", regexpVal.map(v -> o.put("projectId", v))),
                                    optional("branch", regexpVal.map(v -> o.put("branch", v))),
                                    optional("enabled", booleanVal.map(v -> o.put("enabled", v)))))
                        .map(Collections::unmodifiableMap));

    private static final Parser<Atom, Map<String, Object>> githubTriggerRepositoryInfoItemVal =
            orError(githubTriggerRepositoryInfoItem, YamlValueType.GITHUB_REPOSITORY_INFO);

    private static final Parser<Atom, List<Map<String, Object>>> githubTriggerRepositoryInfo =
            betweenTokens(JsonToken.START_ARRAY, JsonToken.END_ARRAY, many1(githubTriggerRepositoryInfoItemVal).map(Seq::toList));

    private static final Parser<Atom, List<Map<String, Object>>> githubTriggerRepositoryInfoVal =
            orError(githubTriggerRepositoryInfo, YamlValueType.ARRAY_OF_GITHUB_REPOSITORY_INFO);

    private static GithubTriggerExclusiveMode validateGithubExclusiveMode(GithubTriggerExclusiveMode e) {
        if (e.groupByProperty() == null && e.group() == null) {
            throw new OneOfMandatoryFieldsNotFoundException("group", "groupBy");
        }
        return e;
    }

    private static final Parser<Atom, GithubTriggerExclusiveMode> githubTriggerExclusiveV2 =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(ImmutableGithubTriggerExclusiveMode::builder,
                            o -> options(
                                    optional("group", stringVal.map(o::group)),
                                    optional("groupBy", stringVal.map(o::groupByProperty)),
                                    optional("mode", enumVal(ExclusiveMode.Mode.class).map(o::mode))))
                            .map(ImmutableGithubTriggerExclusiveMode.Builder::build)
                            .map(TriggersGrammar::validateGithubExclusiveMode));

    private static final Parser<Atom, GithubTriggerExclusiveMode> githubTriggerExclusiveValV2 =
            orError(githubTriggerExclusiveV2, YamlValueType.GITHUB_EXCLUSIVE_MODE);

    private static final Parser<Atom, Map<String, Object>> githubTriggerFilesVal =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                with((Supplier<HashMap<String, Object>>) LinkedHashMap::new,
                        o -> options(
                            optional("added", regexpOrArrayVal.map(v -> o.put("added", v))),
                            optional("removed", regexpOrArrayVal.map(v -> o.put("removed", v))),
                            optional("modified", regexpOrArrayVal.map(v -> o.put("modified", v))),
                            optional("any", regexpOrArrayVal.map(v -> o.put("any", v)))))
                        .map(Collections::unmodifiableMap));

    private static final Parser<Atom, Map<String, Object>> githubTriggerConditionsV2 =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with((Supplier<HashMap<String, Object>>) LinkedHashMap::new,
                            o -> options(
                                mandatory("type", stringVal.map(v -> o.put("type", v))),
                                optional("githubOrg", regexpOrArrayVal.map(v -> o.put("githubOrg", v))),
                                optional("githubRepo", regexpOrArrayVal.map(v -> o.put("githubRepo", v))),
                                optional("githubHost", regexpVal.map(v -> o.put("githubHost", v))),
                                optional("branch", regexpOrArrayVal.map(v -> o.put("branch", v))),
                                optional("sender", regexpOrArrayVal.map(v -> o.put("sender", v))),
                                optional("status", regexpOrArrayVal.map(v -> o.put("status", v))),
                                optional("repositoryInfo", githubTriggerRepositoryInfoVal.map(v -> o.put("repositoryInfo", v))),
                                optional("files", githubTriggerFilesVal.map(v -> o.put("files", v))),
                                optional("payload", mapVal.map(v -> o.put("payload", v)))))
                            .map(Collections::unmodifiableMap));

    private static final Parser<Atom, Map<String, Object>> githubTriggerConditionsValV2 =
            orError(githubTriggerConditionsV2, YamlValueType.GITHUB_TRIGGER_CONDITIONS);

    private static final Parser<Atom, Trigger> githubTriggerV1 = in -> {
        throw new UnsupportedException("Version 1 of GitHub triggers is not supported");
    };

    private static final Parser<Atom, Trigger> githubTriggerV2 =
            with(ImmutableTrigger::builder,
                    o -> options(
                            optional("useInitiator", booleanVal.map(v -> o.putConfiguration("useInitiator", v))),
                            mandatory("entryPoint", stringVal.map(v -> o.putConfiguration("entryPoint", v))),
                            optional("activeProfiles", stringArrayVal.map(o::activeProfiles)),
                            optional("useEventCommitId", booleanVal.map(v -> o.putConfiguration("useEventCommitId", v))),
                            optional("ignoreEmptyPush", booleanVal.map(v -> o.putConfiguration("ignoreEmptyPush", v))),
                            optional("arguments", mapVal.map(o::arguments)),
                            optional("exclusive", githubTriggerExclusiveValV2.map(v -> o.putConfiguration("exclusive", v))),
                            mandatory("conditions", githubTriggerConditionsValV2.map(o::putAllConditions)),
                            mandatory("version", intVal.map(v -> o.putConditions("version", v)))))
                    .map(t -> t.name("github"))
                    .map(ImmutableTrigger.Builder::build);

    private static final Parser<Atom, Trigger> githubTrigger =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    lookup("version", YamlValueType.INT, 2, githubTriggerV2, githubTriggerV1));

    private static final Parser<Atom, Trigger> githubTriggerVal =
            orError(githubTrigger, YamlValueType.GITHUB_TRIGGER);


    private static final Parser<Atom, Map<String, Object>> runAs =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with((Supplier<HashMap<String, Object>>) LinkedHashMap::new,
                            o -> options(
                                    mandatory("withSecret", stringNotEmptyVal.map(v -> o.put("withSecret", v)))))
                            .map(Collections::unmodifiableMap));

    private static final Parser<Atom, Map<String, Object>> runAsVal =
            orError(runAs, YamlValueType.RUN_AS);

    private static final Parser<Atom, Trigger> cronTrigger =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                with(ImmutableTrigger::builder,
                        o -> options(
                                mandatory("spec", stringVal.map(v -> o.putConditions("spec", v))),
                                mandatory("entryPoint", stringVal.map(v -> o.putConfiguration("entryPoint", v))),
                                optional("runAs", runAsVal.map(v -> o.putConfiguration("runAs", v))),
                                optional("activeProfiles", stringArrayVal.map(o::activeProfiles)),
                                optional("timezone", timezoneVal.map(v -> o.putConditions("timezone", v))),
                                optional("arguments", mapVal.map(o::arguments)),
                                optional("exclusive", exclusiveVal.map(v -> o.putConfiguration("exclusive", v))))))
                        .map(t -> t.name("cron"))
                        .map(ImmutableTrigger.Builder::build);

    private static final Parser<Atom, Trigger> cronTriggerVal =
            orError(cronTrigger, YamlValueType.CRON_TRIGGER);

    private static final Parser<Atom, Trigger> manualTrigger =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(ImmutableTrigger::builder,
                            o -> options(
                                    optional("name", stringVal.map(v -> o.putConfiguration("name", v))),
                                    mandatory("entryPoint", stringVal.map(v -> o.putConfiguration("entryPoint", v))),
                                    optional("activeProfiles", stringArrayVal.map(o::activeProfiles)),
                                    optional("arguments", mapVal.map(o::arguments)))))
                    .map(t -> t.name("manual"))
                    .map(ImmutableTrigger.Builder::build);

    private static final Parser<Atom, Trigger> manualTriggerVal =
            orError(manualTrigger, YamlValueType.MANUAL_TRIGGER);

    private static final Parser<Atom, Trigger> oneopsTriggerV1 = in -> {
        throw new UnsupportedException("Version 1 of oneops trigger not supported");
    };

    private static final Parser<Atom, Trigger> oneopsTriggerV2 =
            with(ImmutableTrigger::builder,
                    o -> options(
                            optional("useInitiator", booleanVal.map(v -> o.putConfiguration("useInitiator", v))),
                            mandatory("entryPoint", stringVal.map(v -> o.putConfiguration("entryPoint", v))),
                            optional("activeProfiles", stringArrayVal.map(o::activeProfiles)),
                            optional("arguments", mapVal.map(o::arguments)),
                            optional("exclusive", exclusiveVal.map(v -> o.putConfiguration("exclusive", v))),
                            mandatory("conditions", mapVal.map(o::putAllConditions)),
                            mandatory("version", intVal.map(v -> o.putConditions("version", v)))))
                    .map(t -> t.name("oneops"))
                    .map(ImmutableTrigger.Builder::build);

    private static final Parser<Atom, Trigger> oneopsTrigger =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    lookup("version", YamlValueType.INT, 2, oneopsTriggerV2, oneopsTriggerV1));

    private static final Parser<Atom, Trigger> oneopsTriggerVal =
            orError(oneopsTrigger, YamlValueType.ONEOPS_TRIGGER);

    private static final Parser<Atom, Trigger> genericTriggerV1 = in -> {
        throw new UnsupportedException("Version 1 of generic trigger not supported");
    };

    private static Parser<Atom, Trigger> genericTriggerV2(String triggerName) {
        return with(ImmutableTrigger::builder,
                o -> options(
                        mandatory("entryPoint", stringVal.map(v -> o.putConfiguration("entryPoint", v))),
                        optional("activeProfiles", stringArrayVal.map(o::activeProfiles)),
                        optional("arguments", mapVal.map(o::arguments)),
                        optional("exclusive", exclusiveVal.map(v -> o.putConfiguration("exclusive", v))),
                        mandatory("conditions", mapVal.map(o::conditions)),
                        mandatory("version", intVal.map(v -> o.putConfiguration("version", v)))))
                .map(t -> t.name(triggerName))
                .map(ImmutableTrigger.Builder::build);
    }

    private static Parser<Atom, Trigger> genericTrigger(String triggerName)  {
        return betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                lookup("version", YamlValueType.INT, 2, genericTriggerV2(triggerName), genericTriggerV1));
    }

    private static Parser<Atom, Trigger> genericTriggerVal(String triggerName) {
        return orError(genericTrigger(triggerName), YamlValueType.GENERIC_TRIGGER);
    }

    private static final Parser<Atom, Trigger> triggerDef =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    choice(
                            satisfyField("github", atom -> githubTriggerVal.map(t -> addLocation(t, atom))),
                            satisfyField("cron", atom -> cronTriggerVal.map(t -> addLocation(t, atom))),
                            satisfyField("manual", atom -> manualTriggerVal.map(t -> addLocation(t, atom))),
                            satisfyField("oneops", atom -> oneopsTriggerVal.map(t -> addLocation(t, atom))),
                            satisfyAnyField(YamlValueType.GENERIC_TRIGGER, atom -> genericTriggerVal(atom.name).map(t -> addLocation(t, atom)))));

    private static Trigger addLocation(Trigger t, Atom atom) {
        return Trigger.builder().from(t)
                .location(atom.location)
                .build();
    }

    private static final Parser<Atom, Trigger> triggerVal =
            orError(triggerDef, YamlValueType.TRIGGER);

    private static final Parser<Atom, List<Trigger>> triggers =
            betweenTokens(JsonToken.START_ARRAY, JsonToken.END_ARRAY,
                    many1(triggerVal).map(Seq::toList));

    public static final Parser<Atom, List<Trigger>> triggersVal =
            orError(triggers, YamlValueType.TRIGGERS);

    private TriggersGrammar() {
    }
}
