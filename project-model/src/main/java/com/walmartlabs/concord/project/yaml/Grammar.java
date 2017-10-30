package com.walmartlabs.concord.project.yaml;

import com.fasterxml.jackson.core.JsonToken;
import com.walmartlabs.concord.project.yaml.model.*;
import io.takari.bpm.model.ScriptTask;
import io.takari.parc.Parser;
import io.takari.parc.Seq;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.takari.parc.Combinators.*;

public class Grammar {

    // Misc functions

    private static Parser<Atom, Atom> satisfyToken(JsonToken t) {
        return satisfy((Atom a) -> a.token == t);
    }

    private static Parser<Atom, Atom> satisfyField(String name) {
        return label("Field '" + name + "'", satisfy((Atom a) -> name.equals(a.name)));
    }

    private static <O> Parser<Atom, O> betweenTokens(JsonToken start, JsonToken end, Parser<Atom, O> p) {
        return between(satisfyToken(start), satisfyToken(end), p);
    }

    private static Object toValue(KV<String, Object> kv) {
        Object v = kv.getValue();
        if (v == null && kv.getKey() != null) {
            return false;
        }
        return v;
    }

    private static Map<String, Object> toMap(Seq<KV<String, Object>> values) {
        if (values == null) {
            return Collections.emptyMap();
        }

        return values.stream()
                .collect(Collectors.toMap(Entry::getKey, Grammar::toValue));
    }

    // Grammar rules

    // declare forward references for recursive types
    private static final Parser.Ref<Atom, Object> value = Parser.ref();
    private static final Parser.Ref<Atom, Seq<Object>> arrayOfValues = Parser.ref();
    private static final Parser.Ref<Atom, Map<String, Object>> object = Parser.ref();
    private static final Parser.Ref<Atom, Seq<YamlStep>> steps = Parser.ref();

    // expression := VALUE_STRING ${.*}
    private static final Parser<Atom, Atom> expression = label("Expression", satisfy((Atom a) -> {
        if (a.token != JsonToken.VALUE_STRING) {
            return false;
        }

        String s = (String) a.value;
        return s != null && s.startsWith("${") && s.endsWith("}");
    }));

    // value := VALUE_STRING | VALUE_NUMBER_INT | VALUE_NUMBER_FLOAT | VALUE_TRUE | VALUE_FALSE | VALUE_NULL | arrayOfValues | object
    private static Parser<Atom, Object> _val(JsonToken t) {
        return satisfyToken(t).map(a -> a.value);
    }

    static {
        value.set(choice(
                choice(
                        _val(JsonToken.VALUE_STRING),
                        _val(JsonToken.VALUE_NUMBER_INT),
                        _val(JsonToken.VALUE_NUMBER_FLOAT),
                        _val(JsonToken.VALUE_TRUE),
                        _val(JsonToken.VALUE_FALSE),
                        _val(JsonToken.VALUE_NULL)
                ),
                arrayOfValues,
                object
        ));
    }

    // arrayOfValues := START_ARRAY value* END_ARRAY
    static {
        arrayOfValues.set(label("Array of values",
                betweenTokens(JsonToken.START_ARRAY, JsonToken.END_ARRAY,
                        many(value))));
    }

    // object := START_OBJECT (FIELD_NAME value)* END_OBJECT
    static {
        object.set(label("Object",
                betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                        many(satisfyToken(JsonToken.FIELD_NAME).bind(a ->
                                value.map(v -> new KV<>(a.name, v)))))
                        .map(Grammar::toMap)));
    }

    // identifier := VALUE_STRING
    // TODO validate identifiers
    private static final Parser<Atom, Atom> identifier = label("Identifier",
            satisfyToken(JsonToken.VALUE_STRING));

    // formName := VALUE_STRING ^form \((.*)\)$
    private static final Pattern FORM_NAME_PATTERN = Pattern.compile("^form \\((.*)\\)$");
    private static final Parser<Atom, String> formName = label("Form name",
            satisfy((Atom a) -> {
                if (a.token != JsonToken.FIELD_NAME) {
                    return false;
                }

                String s = a.name;
                Matcher m = FORM_NAME_PATTERN.matcher(s);
                return m.matches();
            }).map(a -> {
                String s = a.name;
                Matcher m = FORM_NAME_PATTERN.matcher(s);
                m.matches();
                return m.group(1);
            }));

    // outField := FIELD_NAME "out" identifier
    private static final Parser<Atom, KV<String, Object>> outField = label("Out variable",
            satisfyField("out").then(identifier)
                    .map(a -> new KV<>("out", a.value)));

    // errorBlock := FIELD_NAME "error" steps
    private static final Parser<Atom, KV<String, Object>> errorBlock = label("Error handling block",
            satisfyField("error").then(steps)
                    .map(v -> new KV<>("error", v)));

    // kv := FIELD_NAME value
    private static final Parser<Atom, KV<String, Object>> kv = label("Key-value pair",
            satisfyToken(JsonToken.FIELD_NAME).bind(a ->
                    value.map(v -> new KV<>(a.name, v))));

    // inVars := FIELD_NAME "in" START_OBJECT (kv)+ END_OBJECT
    private static final Parser<Atom, KV<String, Object>> inVars = label("IN variables",
            satisfyField("in").then(
                    betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                            many1(kv))).map(v -> new KV<>("in", v)));

    // outVars := FIELD_NAME "out" START_OBJECT (kv)+ END_OBJECT
    private static final Parser<Atom, KV<String, Object>> outVars = label("OUT variables",
            satisfyField("out").then(
                    betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                            many1(kv))).map(v -> new KV<>("out", v)));

    // exprOptions := (outField | errorBlock)*
    private static final Parser<Atom, Map<String, Object>> exprOptions = label("Expression options",
            many(choice(errorBlock, outField)).map(Grammar::toMap));

    // taskOptions := (inVars | outVars | outField | errorBlock)*
    private static final Parser<Atom, Map<String, Object>> taskOptions = label("Task options",
            many(choice(inVars, outVars, errorBlock, outField)).map(Grammar::toMap));

    // callOptions := (inVars | outVars | errorBlock)*
    private static final Parser<Atom, Map<String, Object>> callOptions = label("Process call options",
            many(choice(inVars, outVars, errorBlock)).map(Grammar::toMap));

    // groupOptions := (errorBlock)*
    private static final Parser<Atom, Map<String, Object>> groupOptions = label("Group options",
            many(errorBlock).map(Grammar::toMap));

    // formCallOptions := (inVars | errorBlock)*
    private static final Parser<Atom, Map<String, Object>> formCallOptions = label("Form call options",
            many(kv).map(Grammar::toMap));

    // exprShort := expression
    private static final Parser<Atom, YamlStep> exprShort = label("Expression (short form)",
            expression.map(a -> new YamlExpressionStep(a.location, (String) a.value)));

    // exprFull := FIELD_NAME "expr" expression exprOptions
    private static final Parser<Atom, YamlStep> exprFull = label("Expression (full form)",
            satisfyField("expr").then(expression).bind(a ->
                    exprOptions.map(options -> new YamlExpressionStep(a.location, (String) a.value, options))));

    // taskFull := FIELD_NAME "task" VALUE_STRING taskOptions
    private static final Parser<Atom, YamlStep> taskFull = label("Task (full form)",
            satisfyField("task").then(satisfyToken(JsonToken.VALUE_STRING)).bind(a ->
                    taskOptions.map(options -> new YamlTaskStep(a.location, (String) a.value, options))));

    // taskShort := FIELD_NAME literal
    private static final Parser<Atom, YamlStep> taskShort = label("Task (short form)",
            satisfyToken(JsonToken.FIELD_NAME).bind(a ->
                    value.map(arg -> new YamlTaskShortStep(a.location, a.name, arg))));

    // ifExpr := FIELD_NAME "if" expression FIELD_NAME "then" steps (FIELD_NAME "else" steps)?
    private static final Parser<Atom, YamlStep> ifExpr = label("IF expression",
            satisfyField("if").then(expression).bind(a ->
                    satisfyField("then").then(steps).bind(thenSteps ->
                            option(satisfyField("else").then(steps)
                                            .map(elseSteps -> new YamlIfExpr(a.location, (String) a.value, thenSteps, elseSteps)),
                                    new YamlIfExpr(a.location, (String) a.value, thenSteps, Seq.empty())))));

    // switchExpr := FIELD_NAME "switch" expression (FIELD_NAME steps)* (FIELD_NAME "default" steps)?
    private static final Parser<Atom, Seq<KV<String, Seq<YamlStep>>>> switchOptions = label("SWITCH options",
            many1(satisfyToken(JsonToken.FIELD_NAME).bind(a -> steps.map(v -> new KV<>(a.name, v)))));

    private static final Parser<Atom, YamlStep> switchExpr = label("SWITCH expression",
            satisfyField("switch").then(expression).bind(a ->
                    switchOptions.map(options ->
                            new YamlSwitchExpr(a.location, (String) a.value, options))));

    // returnExpr := VALUE_STRING "return"
    private static final Parser<Atom, YamlStep> returnExpr = label("Return keyword",
            satisfy((Atom a) -> {
                if (a.token != JsonToken.VALUE_STRING) {
                    return false;
                }

                String s = (String) a.value;
                return "return".equals(s);
            }).map(a -> new YamlReturn(a.location)));

    // errorReturn := FIELD_NAME "return" VALUE_STRING
    private static final Parser<Atom, YamlStep> errorReturn = label("Error end event",
            satisfyField("return").then(satisfyToken(JsonToken.VALUE_STRING))
                    .map(a -> new YamlReturn(a.location, (String) a.value)));

    // group := FIELD_NAME ":" steps groupOptions
    private static final Parser<Atom, YamlStep> group = label("Group of steps",
            choice(satisfyField(":"), satisfyField("try")).bind(a -> steps.bind(items ->
                    groupOptions.map(options -> new YamlGroup(a.location, items, options)))));

    // callFull := FIELD_NAME "call" VALUE_STRING callOptions
    private static final Parser<Atom, YamlStep> callFull = label("Process call (full form)",
            satisfyField("call").then(satisfyToken(JsonToken.VALUE_STRING)).bind(a ->
                    callOptions.map(options -> new YamlCall(a.location, (String) a.value, options))));

    // callProc := VALUE_STRING
    private static final Parser<Atom, YamlStep> callProc = label("Process call",
            satisfyToken(JsonToken.VALUE_STRING).map(a -> new YamlCall(a.location, (String) a.value, null)));

    // event := FIELD_NAME "event" VALUE_STRING
    private static final Parser<Atom, YamlStep> event = label("Event (debug only)",
            satisfyField("event").then(satisfyToken(JsonToken.VALUE_STRING))
                    .map(a -> new YamlEvent(a.location, (String) a.value)));

    // script := FIELD_NAME "script" VALUE_STRING (FIELD_NAME "body" VALUE_STRING)?
    private static final Parser<Atom, YamlStep> script = label("Script",
            satisfyField("script").then(satisfyToken(JsonToken.VALUE_STRING))
                    .bind(a -> option(satisfyField("body").then(satisfyToken(JsonToken.VALUE_STRING))
                                    .map(b -> new YamlScript(a.location, ScriptTask.Type.CONTENT, (String) a.value, (String) b.value)),
                            new YamlScript(a.location, ScriptTask.Type.REFERENCE, null, (String) a.value))));

    // formCall := FIELD_NAME "form" VALUE_STRING formCallOptions
    private static final Parser<Atom, YamlStep> formCall = label("Form call",
            satisfyField("form").then(satisfyToken(JsonToken.VALUE_STRING)).bind(a ->
                    formCallOptions.map(options -> new YamlFormCall(a.location, (String) a.value, options))));

    // inVars := FIELD_NAME "set" START_OBJECT (kv)+ END_OBJECT
    private static final Parser<Atom, YamlStep> vars = label("Variables",
            satisfyField("set")
                    .bind(a ->
                            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT, many1(kv))
                                    .map(Grammar::toMap)
                                    .map(v -> new YamlSetVariablesStep(a.location, v))));

    private static final Parser<Atom, Map<String, Object>> dockerOptions = label("Docker call options",
            many(kv).map(Grammar::toMap));

    // docker := FIELD_NAME "docker" VALUE_STRING dockerOptions
    private static final Parser<Atom, YamlStep> docker = label("Docker call",
            satisfyField("docker").then(satisfyToken(JsonToken.VALUE_STRING)).bind(a ->
                    dockerOptions.map(options ->
                            new YamlDockerStep(a.location, (String) a.value,
                                    (String) options.get("cmd"),
                                    (boolean) options.getOrDefault("forcePull", true),
                                    (Map<String, Object>) options.get("env")))));

    // stepObject := START_OBJECT docker | group | ifExpr | exprFull | formCall | vars | taskFull | callFull | event | script | taskShort | vars END_OBJECT
    private static final Parser<Atom, YamlStep> stepObject = label("Process definition step (complex)",
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    choice(choice(docker, group, switchExpr, ifExpr, exprFull, formCall, vars), taskFull, callFull, event, errorReturn, script, taskShort)));

    // step := returnExpr | exprShort | callProc | stepObject
    private static final Parser<Atom, YamlStep> step = choice(returnExpr, exprShort, callProc, stepObject);

    // steps := START_ARRAY step+ END_ARRAY
    static {
        steps.set(label("Process definition step(s)",
                betweenTokens(JsonToken.START_ARRAY, JsonToken.END_ARRAY,
                        many1(step))));
    }

    // formField := START_OBJECT FIELD_NAME object END_OBJECT
    private static final Parser<Atom, YamlFormField> formField = label("Form field",
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    satisfyToken(JsonToken.FIELD_NAME).bind(a ->
                            object.map(options -> new YamlFormField(a.location, a.name, options)))));

    // formFields := START_ARRAY formField+ END_ARRAY
    private static final Parser<Atom, Seq<YamlFormField>> formFields = label("Form fields",
            betweenTokens(JsonToken.START_ARRAY, JsonToken.END_ARRAY,
                    many1(formField)));

    // procDef := FIELD_NAME steps
    private static final Parser<Atom, YamlProcessDefinition> procDef = label("Process definition",
            satisfyToken(JsonToken.FIELD_NAME).bind(a ->
                    steps.map(steps -> new YamlProcessDefinition(a.name, steps))));

    // formDef := formName formFields
    private static final Parser<Atom, YamlFormDefinition> formDef = label("Form definition",
            formName.bind(name ->
                    formFields.map(fields -> new YamlFormDefinition(name, fields))));

    // defs := START_OBJECT (formDef | procDef)+ END_OBJECT
    private static final Parser<Atom, Seq<YamlDefinition>> defs = label("Process and form definitions",
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    many1(choice(formDef, procDef))));

    public static Parser<Atom, YamlStep> getProcessStep() {
        return step;
    }

    public static Parser<Atom, YamlFormField> getFormField() {
        return formField;
    }

    public static Parser<Atom, Seq<YamlDefinition>> getDefinitions() {
        return defs;
    }
}
