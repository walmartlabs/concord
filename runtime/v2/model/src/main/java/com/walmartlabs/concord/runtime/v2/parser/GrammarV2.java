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
import com.walmartlabs.concord.runtime.v2.exception.InvalidValueException;
import com.walmartlabs.concord.runtime.v2.exception.InvalidValueTypeException;
import com.walmartlabs.concord.runtime.v2.model.Step;
import io.takari.parc.Parser;
import io.takari.parc.Seq;

import java.io.Serializable;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.runtime.v2.parser.CheckpointGrammar.checkpoint;
import static com.walmartlabs.concord.runtime.v2.parser.ConditionalExpressionsGrammar.ifExpr;
import static com.walmartlabs.concord.runtime.v2.parser.ConditionalExpressionsGrammar.switchExpr;
import static com.walmartlabs.concord.runtime.v2.parser.ExitGrammar.exit;
import static com.walmartlabs.concord.runtime.v2.parser.ExpressionGrammar.exprFull;
import static com.walmartlabs.concord.runtime.v2.parser.ExpressionGrammar.exprShort;
import static com.walmartlabs.concord.runtime.v2.parser.FlowCallGrammar.callFull;
import static com.walmartlabs.concord.runtime.v2.parser.FormsGrammar.callForm;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GroupOfStepsGrammar.group;
import static com.walmartlabs.concord.runtime.v2.parser.LogGrammar.logStep;
import static com.walmartlabs.concord.runtime.v2.parser.LogGrammar.logYamlStep;
import static com.walmartlabs.concord.runtime.v2.parser.ParallelGrammar.parallelBlock;
import static com.walmartlabs.concord.runtime.v2.parser.ReturnGrammar.returnStep;
import static com.walmartlabs.concord.runtime.v2.parser.ScriptGrammar.script;
import static com.walmartlabs.concord.runtime.v2.parser.SetVariablesGrammar.setVars;
import static com.walmartlabs.concord.runtime.v2.parser.SuspendGrammar.suspendStep;
import static com.walmartlabs.concord.runtime.v2.parser.TaskGrammar.taskFull;
import static com.walmartlabs.concord.runtime.v2.parser.ThrowGrammar.throwStep;
import static io.takari.parc.Combinators.*;

public final class GrammarV2 {

    public static final Parser.Ref<Atom, YamlValue> value = Parser.ref();
    public static final Parser.Ref<Atom, YamlList> arrayOfValues = Parser.ref();
    public static final Parser.Ref<Atom, YamlObject> object = Parser.ref();

    public static final Parser<Atom, Serializable> anyVal = value.map(YamlValue::getValue);
    public static final Parser<Atom, Integer> intVal = value.map(v -> v.getValue(YamlValueType.INT));
    public static final Parser<Atom, String> stringVal = value.map(v -> v.getValue(YamlValueType.STRING));
    public static final Parser<Atom, Boolean> booleanVal = value.map(v -> v.getValue(YamlValueType.BOOLEAN));
    public static final Parser<Atom, Map<String, Serializable>> mapVal = value.map(v -> v.getValue(YamlValueType.OBJECT));
    public static final Parser<Atom, List<String>> regexpArrayVal = value.map(v -> asList(v, YamlValueType.ARRAY_OF_PATTERN).getListValue(GrammarV2::regexpConverter));
    public static final Parser<Atom, String> regexpVal = value.map(GrammarV2::regexpConverter);
    public static final Parser<Atom, List<String>> stringArrayVal = value.map(v -> asList(v, YamlValueType.ARRAY_OF_STRING).getListValue(YamlValueType.STRING));
    public static final Parser<Atom, Set<String>> stringSetVal = stringArrayVal.map(HashSet::new);
    public static final Parser<Atom, Serializable> nonNullVal = value.map(v -> {
        assertNotNull(v);
        return v.getValue();
    });
    public static final Parser<Atom, Integer> maybeInt = _val(JsonToken.VALUE_NUMBER_INT).map(v -> v.getValue(YamlValueType.INT));
    public static final Parser<Atom, String> maybeString = _val(JsonToken.VALUE_STRING).map(v -> v.getValue(YamlValueType.STRING));
    public static final Parser<Atom, List<String>> maybeStringArray = arrayOfValues.map(v -> v.getListValue(YamlValueType.STRING));
    public static final Parser<Atom, Map<String, Serializable>> maybeMap = object.map(YamlObject::getValue);
    public static final Parser<Atom, Object> regexpOrArrayVal = value.map(GrammarV2::regexpOrArrayConverter);
    public static final Parser<Atom, Duration> durationVal = value.map(GrammarV2::durationConverter);
    public static final Parser<Atom, String> timezoneVal = value.map(GrammarV2::timezoneConverter);
    public static final Parser<Atom, List<String>> stringOrArrayVal = value.map(GrammarV2::stringOrArrayConverter);
    public static final Parser<Atom, String> stringNotEmptyVal = value.map(v -> {
        String vv = v.getValue(YamlValueType.STRING);
        if (vv.trim().isEmpty()) {
            throw new InvalidValueTypeException.Builder()
                    .location(v.getLocation())
                    .expected(YamlValueType.NON_EMPTY_STRING)
                    .actual(v.getType())
                    .message("Empty value")
                    .build();
        }
        return vv;
    });

    public static <E extends Enum<E>> Parser<Atom, E> enumVal(Class<E> enumData) {
        return enumVal(enumData, String::equals);
    }

    public static <E extends Enum<E>> Parser<Atom, E> enumVal(Class<E> enumData,
                                                              BiPredicate<String, String> cmp) {
        return value.map(vv -> {
            String v = vv.getValue(YamlValueType.STRING);

            for (E enumVal : enumData.getEnumConstants()) {
                if (cmp.test(enumVal.name(), v)) {
                    return enumVal;
                }
            }

            throw InvalidValueException.builder()
                    .actual(v)
                    .expected(Arrays.stream(enumData.getEnumConstants()).map(Enum::name).collect(Collectors.toList()))
                    .location(vv.getLocation())
                    .build();
        });
    }

    public static final Parser.Ref<Atom, List<Step>> stepsVal = Parser.ref();

    @SuppressWarnings("rawtypes")
    private static YamlValueType toType(JsonToken t) {
        switch (t) {
            case VALUE_STRING:
                return YamlValueType.STRING;
            case VALUE_NUMBER_INT:
                return YamlValueType.INT;
            case VALUE_NUMBER_FLOAT:
                return YamlValueType.FLOAT;
            case VALUE_FALSE:
            case VALUE_TRUE:
                return YamlValueType.BOOLEAN;
            case VALUE_NULL:
                return YamlValueType.NULL;
            default:
                throw new IllegalArgumentException("Unknown type: " + t);
        }
    }

    // value := VALUE_STRING | VALUE_NUMBER_INT | VALUE_NUMBER_FLOAT | VALUE_TRUE | VALUE_FALSE | VALUE_NULL | arrayOfValues | object
    @SuppressWarnings("unchecked")
    private static Parser<Atom, YamlValue> _val(JsonToken t) {
        return satisfyToken(t).map(a -> new YamlValue(a.value, toType(t), a.location));
    }

    // arrayOfValues := START_ARRAY value* END_ARRAY
    static {
        arrayOfValues.set(label("Array of values",
                testToken(JsonToken.START_ARRAY).bind(t ->
                        betweenTokens(JsonToken.START_ARRAY, JsonToken.END_ARRAY,
                                many(value).map(a -> new YamlList(a.toList(), t.location)))
                )));
    }

    // object := START_OBJECT (FIELD_NAME value)* END_OBJECT
    static {
        object.set(
                testToken(JsonToken.START_OBJECT).bind(t ->
                        betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                                many(satisfyToken(JsonToken.FIELD_NAME).bind(a ->
                                        value.map(v -> new KV<>(a.name, v)))))
                                .map(a -> new YamlObject(valueToMap(a), t.location))));
    }

    static {
        value.set(choice(choice(
                _val(JsonToken.VALUE_STRING),
                _val(JsonToken.VALUE_NUMBER_INT),
                _val(JsonToken.VALUE_NUMBER_FLOAT),
                _val(JsonToken.VALUE_TRUE),
                _val(JsonToken.VALUE_FALSE),
                _val(JsonToken.VALUE_NULL)),
                arrayOfValues,
                object
        ));
    }

    private static final Parser<Atom, Step> stepObject =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    choice(choice(parallelBlock, group, exprFull), choice(taskFull, script, callFull, callForm),
                            choice(checkpoint, ifExpr, switchExpr, setVars), logStep, logYamlStep, throwStep, suspendStep));

    // step := exit | exprShort | parallelBlock | stepObject
    private static final Parser<Atom, Step> step = orError(choice(exit, returnStep, exprShort, stepObject), YamlValueType.STEP);

    // steps := START_ARRAY step+ END_ARRAY
    static {
        stepsVal.set(
                orError(betweenTokens(JsonToken.START_ARRAY, JsonToken.END_ARRAY, many1(step).map(Seq::toList)),
                        YamlValueType.ARRAY_OF_STEP));
    }

    public static Parser<Atom, Step> getProcessStep() {
        return step;
    }

    public static <K, V> Map<K, V> toMap(Seq<KV<K, V>> values) {
        Map<K, V> m = new LinkedHashMap<>();
        values.stream().forEach(kv -> m.put(kv.getKey(), kv.getValue()));
        return m;
    }

    public static YamlValue assertNotNull(YamlValue v) {
        if (v.getType() != YamlValueType.NULL) {
            return v;
        }

        throw new InvalidValueTypeException.Builder()
                .location(v.getLocation())
                .expected(YamlValueType.NON_NULL)
                .actual(v.getType())
                .build();
    }

    private static Map<String, YamlValue> valueToMap(Seq<KV<String, YamlValue>> values) {
        if (values == null) {
            return Collections.emptyMap();
        }

        Map<String, YamlValue> m = new LinkedHashMap<>();
        values.stream().forEach(kv -> m.put(kv.getKey(), kv.getValue()));
        return m;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static YamlList asList(YamlValue value, YamlValueType listType) {
        if (YamlValueType.ARRAY != value.getType()) {
            // will throw exception
            value.getValue(listType);
        }
        return (YamlList) value;
    }

    private static String regexpConverter(YamlValue v) {
        if (v.getType() != YamlValueType.STRING) {
            // will throw exception
            v.getValue(YamlValueType.PATTERN);
        }

        String p = v.getValue(YamlValueType.STRING);
        try {
            Pattern.compile(p);
            return p;
        } catch (PatternSyntaxException e) {
            throw new InvalidValueTypeException.Builder()
                    .location(v.getLocation())
                    .expected(YamlValueType.PATTERN)
                    .actual(v.getType())
                    .message(e.getMessage())
                    .build();
        }
    }

    private static Object regexpOrArrayConverter(YamlValue v) {
        if (v.getType() == YamlValueType.STRING) {
            return regexpConverter(v);
        }

        YamlList list = asList(v, YamlValueType.REGEXP_OR_ARRAY);
        return list.getListValue(GrammarV2::regexpConverter);
    }

    private static Duration durationConverter(YamlValue v) {
        if (v.getType() != YamlValueType.STRING) {
            // will throw exception
            v.getValue(YamlValueType.DURATION);
        }

        String maybePattern = v.getValue(YamlValueType.STRING);
        try {
            return Duration.parse(maybePattern);
        } catch (DateTimeParseException e) {
            throw new InvalidValueTypeException.Builder()
                    .location(v.getLocation())
                    .expected(YamlValueType.DURATION)
                    .actual(v.getType())
                    .message(e.getMessage())
                    .build();
        }
    }

    private static String timezoneConverter(YamlValue v) {
        if (v.getType() != YamlValueType.STRING) {
            // will throw exception
            v.getValue(YamlValueType.TIMEZONE);
        }

        String timezone = v.getValue(YamlValueType.STRING);

        boolean valid = Arrays.asList(TimeZone.getAvailableIDs()).contains(timezone);
        if (valid) {
            return timezone;
        }

        throw new InvalidValueTypeException.Builder()
                .location(v.getLocation())
                .expected(YamlValueType.TIMEZONE)
                .actual(v.getType())
                .message("Unknown timezone: '" + timezone + "'")
                .build();
    }

    private static List<String> stringOrArrayConverter(YamlValue v) {
        if (v.getType() == YamlValueType.STRING) {
            return Collections.singletonList(v.getValue());
        }

        YamlList list = asList(v, YamlValueType.STRING_OR_ARRAY);
        return list.getListValue(YamlValueType.STRING);
    }

    private GrammarV2() {
    }
}
