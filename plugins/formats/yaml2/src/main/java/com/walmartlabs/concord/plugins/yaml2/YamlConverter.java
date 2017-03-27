package com.walmartlabs.concord.plugins.yaml2;

import com.fasterxml.jackson.core.JsonLocation;
import com.walmartlabs.concord.plugins.yaml2.model.*;
import io.takari.bpm.model.*;
import io.takari.bpm.model.ProcessDefinitionBuilder.Fork;
import io.takari.bpm.model.form.DefaultFormFields.DecimalField;
import io.takari.bpm.model.form.DefaultFormFields.IntegerField;
import io.takari.bpm.model.form.DefaultFormFields.StringField;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.bpm.model.form.FormExtension;
import io.takari.bpm.model.form.FormField;
import io.takari.bpm.model.form.FormField.Cardinality;
import io.takari.bpm.model.form.FormField.Option;
import io.takari.parc.Seq;

import java.io.Serializable;
import java.util.*;

public class YamlConverter {

    public static ProcessDefinition convert(YamlProcessDefinition def) throws YamlConverterException {
        ProcessDefinitionBuilder.Process proc = ProcessDefinitionBuilder.newProcess(def.getName());
        apply(proc, def.getSteps());
        return proc.end();
    }

    public static FormDefinition convert(YamlFormDefinition def) throws YamlConverterException {
        List<FormField> fields = new ArrayList<>();
        for (YamlFormField f : def.getFields().toList()) {
            fields.add(convert(f));
        }
        return new FormDefinition(def.getName(), fields);
    }

    private static FormField convert(YamlFormField f) throws YamlConverterException {
        Map<String, Object> opts = f.getOptions();

        // common parameters
        String label = (String) opts.get("label");
        String valueExpr = (String) opts.get("expr");
        if (valueExpr != null && !valueExpr.isEmpty()) {
            assertExpression(valueExpr, f.getLocation());
        }

        // type-specific options
        Map<Option<?>, Object> options = new HashMap<>();
        TypeInfo tInfo = getFieldType(f);
        switch (tInfo.type) {
            case StringField.TYPE: {
                options.put(StringField.PATTERN, opts.get("pattern"));
                break;
            }
            case IntegerField.TYPE: {
                options.put(IntegerField.MIN, coerceToLong(opts.get("min")));
                options.put(IntegerField.MAX, coerceToLong(opts.get("max")));
                break;
            }
            case DecimalField.TYPE: {
                options.put(DecimalField.MIN, coerceToDouble(opts.get("min")));
                options.put(DecimalField.MAX, coerceToDouble(opts.get("max")));
                break;
            }
            default:
                throw new YamlConverterException("Unknown field type: " + tInfo.type + " @ " + f.getLocation());
        }

        return new FormField.Builder(f.getName(), tInfo.type)
                .label(label)
                .valueExpr(valueExpr)
                .cardinality(tInfo.cardinality)
                .options(options)
                .build();
    }

    private static TypeInfo getFieldType(YamlFormField f) throws YamlConverterException {
        Object v = f.getOption("type");
        if (!(v instanceof String)) {
            JsonLocation loc = f.getLocation();
            throw new YamlConverterException("Expected a field type @ " + loc);
        }
        return TypeInfo.parse((String) v);
    }

    private static ProcessDefinitionBuilder.Seq apply(ProcessDefinitionBuilder.Seq proc, Seq<YamlStep> steps) throws YamlConverterException {
        for (YamlStep s : steps.toList()) {
            proc = apply(proc, s);
        }
        return proc;
    }

    private static ProcessDefinitionBuilder.Seq apply(ProcessDefinitionBuilder.Seq proc, YamlStep s) throws YamlConverterException {
        if (s instanceof YamlExpressionStep) {
            YamlExpressionStep expr = (YamlExpressionStep) s;

            Set<VariableMapping> out = null;
            String outVar = getOutField(expr.getOptions());
            if (outVar != null) {
                out = Collections.singleton(new VariableMapping(ServiceTask.EXPRESSION_RESULT_VAR, null, outVar));
            }

            proc = sourceMap(proc.task(expr.getExpr(), null, out), s, "Expression");

            return applyErrorBlock(proc, expr.getOptions(), joinName(s));
        } else if (s instanceof YamlTaskStep) {
            YamlTaskStep task = (YamlTaskStep) s;

            Set<VariableMapping> in = getInVars(task.getOptions());
            Set<VariableMapping> out = getOutVars(task.getOptions());

            String expr = "${" + task.getKey() + "}";
            proc = sourceMap(proc.task(ExpressionType.DELEGATE, expr, in, out), s, "Task");

            return applyErrorBlock(proc, task.getOptions(), joinName(s));
        } else if (s instanceof YamlTaskShortStep) {
            YamlTaskShortStep task = (YamlTaskShortStep) s;

            ELCall call = createELCall(task.getKey(), task.getArg());
            return sourceMap(proc.task(ExpressionType.SIMPLE, call.expression, call.args, null), s, "Task");
        } else if (s instanceof YamlIfExpr) {
            // ... --> exclusiveGate ---> expr --> thenSteps+ ---> end
            //                      \                            /
            //                       ------------> elseSteps+ -->
            proc = sourceMap(proc.exclusiveGate(), s, "IF expression");

            YamlIfExpr ifExpr = (YamlIfExpr) s;
            String joinName = joinName(s);

            Fork<? extends ProcessDefinitionBuilder.Seq> thenFork = proc.fork().flowExpr(ifExpr.getExpr());
            thenFork = (Fork<? extends ProcessDefinitionBuilder.Seq>) apply(thenFork, ifExpr.getThenSteps());
            proc = thenFork.joinTo(joinName);

            if (ifExpr.getElseSteps() != null) {
                Fork<? extends ProcessDefinitionBuilder.Seq> elseFork = proc.fork();
                elseFork = (Fork<? extends ProcessDefinitionBuilder.Seq>) apply(elseFork, ifExpr.getElseSteps());
                proc = elseFork.joinTo(joinName);
            }

            return proc.joinAll(joinName);
        } else if (s instanceof YamlReturn) {
            proc.end();
            sourceMap(proc, s, "RETURN statement");
            return proc;
        } else if (s instanceof YamlGroup) {
            YamlGroup g = (YamlGroup) s;

            ProcessDefinitionBuilder.Sub<? extends ProcessDefinitionBuilder.Seq> sub = proc.sub();
            sub = (ProcessDefinitionBuilder.Sub<? extends ProcessDefinitionBuilder.Seq>) apply(sub, g.getSteps());
            proc = sub.end();

            proc = sourceMap(proc, s, "Group");

            return applyErrorBlock(proc, g.getOptions(), joinName(s));
        } else if (s instanceof YamlEvent) {
            YamlEvent e = (YamlEvent) s;
            return sourceMap(proc.catchEvent(e.getName()), s, "Event");
        } else if (s instanceof YamlFormCall) {
            YamlFormCall c = (YamlFormCall) s;
            return proc.userTask(Arrays.asList(new FormExtension(c.getKey())));
        } else if (s instanceof YamlCall) {
            YamlCall c = (YamlCall) s;

            String target = c.getProc();
            if (target.startsWith("${")) {
                throw new YamlConverterException("Invalid call: " + target + ". It looks like an expression. @ " + c.getLocation());
            }

            return sourceMap(proc.call(target, true), s, "Call");
        } else if (s instanceof YamlScript) {
            YamlScript c = (YamlScript) s;
            return sourceMap(proc.script(c.getType(), c.getLanguage(), c.getBody()), s, "Script");
        } else {
            throw new YamlConverterException("Unknown step type: " + s.getClass());
        }
    }

    private static String joinName(YamlStep s) {
        JsonLocation loc = s.getLocation();
        return "join_" + loc.getLineNr() + "_" + loc.getColumnNr();
    }

    private static ProcessDefinitionBuilder.Seq sourceMap(ProcessDefinitionBuilder.Seq proc, YamlStep s, String desc) {
        JsonLocation l = s.getLocation();
        if (l == null) {
            return proc;
        }

        return proc.sourceMap(SourceMap.Significance.HIGH, l.getLineNr(), l.getColumnNr(), desc);
    }

    private static Seq<YamlStep> getErrorBlock(Map<String, Object> options) {
        if (options == null) {
            return null;
        }

        Object o = options.get("error");
        if (o == null) {
            return null;
        }

        return (Seq<YamlStep>) o;
    }

    private static ProcessDefinitionBuilder.Seq applyErrorBlock(ProcessDefinitionBuilder.Seq proc,
                                                                Map<String, Object> options,
                                                                String joinName) throws YamlConverterException {

        Seq<YamlStep> steps = getErrorBlock(options);
        if (steps != null) {
            Fork<? extends ProcessDefinitionBuilder.Seq> fork = proc.boundaryEvent();
            apply(fork, steps);
            proc = fork.joinTo(joinName);
            proc = proc.joinPoint(joinName);
        }

        return proc;
    }

    private static String getString(Map<String, Object> options, String k) {
        if (options == null) {
            return null;
        }

        return (String) options.get(k);
    }

    private static String getOutField(Map<String, Object> options) {
        return getString(options, "out");
    }

    private static Set<VariableMapping> getVarMap(Map<String, Object> options, String key) {
        if (options == null) {
            return null;
        }

        Seq<KV<String, Object>> s = (Seq<KV<String, Object>>) options.get(key);
        if (s == null) {
            return null;
        }

        Set<VariableMapping> result = new HashSet<>();
        for (KV<String, Object> kv : s.toList()) {
            String target = kv.getKey();

            String sourceExpr = null;
            Object sourceValue = null;

            Object v = kv.getValue();
            if (isExpression(v)) {
                sourceExpr = v.toString();
            } else {
                sourceValue = v;
            }

            result.add(new VariableMapping(null, sourceExpr, sourceValue, target));
        }

        return result;
    }

    private static Set<VariableMapping> getInVars(Map<String, Object> options) {
        return getVarMap(options, "in");
    }

    private static Set<VariableMapping> getOutVars(Map<String, Object> options) {
        return getVarMap(options, "out");
    }

    private static ELCall createELCall(String task, Object args) {
        StringBuilder b = new StringBuilder("${");
        b.append(task).append(".call(");

        Set<VariableMapping> maps = new HashSet<>();

        if (args != null) {
            args = deepConvert(args);

            if (args instanceof List) {
                int idx = 0;
                for (Iterator<Object> i = ((List) args).iterator(); i.hasNext(); ) {
                    String k = "__" + idx++;
                    Object v = i.next();
                    maps.add(new VariableMapping(null, null, v, k, true));

                    b.append(k);
                    if (i.hasNext()) {
                        b.append(", ");
                    }
                }
            } else {
                String k = "__0";
                if (isExpression(args)) {
                    String s = args.toString().trim();
                    maps.add(new VariableMapping(null, s, null, k));
                } else {
                    maps.add(new VariableMapping(null, null, args, k, true));
                }
                b.append(k);
            }
        }

        b.append(")}");

        return new ELCall(b.toString(), maps.isEmpty() ? null : maps);
    }

    private static boolean isExpression(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof String)) {
            return false;
        }

        String s = (String) o;
        int i = s.indexOf("${");
        return i >= 0 && s.indexOf("}") > i;
    }

    private static Object deepConvert(Object o) {
        if (o instanceof Seq) {
            List<Object> src = ((Seq) o).toList();

            List<Object> dst = new ArrayList<>(src.size());
            for (Object s : src) {
                dst.add(deepConvert(s));
            }

            return dst;
        } else if (o instanceof Map) {
            Map<Object, Object> src = (Map<Object, Object>) o;

            Map<Object, Object> dst = new HashMap<>(src.size());
            for (Map.Entry<Object, Object> e : src.entrySet()) {
                dst.put(e.getKey(), deepConvert(e.getValue()));
            }

            return dst;
        }

        return o;
    }

    private static Long coerceToLong(Object v) {
        if (v == null) {
            return null;
        }

        if (v instanceof Long) {
            return (Long) v;
        }

        if (v instanceof Integer) {
            return ((Integer) v).longValue();
        }

        throw new IllegalArgumentException("Can't coerce '" + v + "' to long");
    }

    private static Double coerceToDouble(Object v) {
        if (v == null) {
            return null;
        }

        if (v instanceof Double) {
            return (Double) v;
        }

        if (v instanceof Float) {
            return ((Float) v).doubleValue();
        }

        if (v instanceof Integer) {
            return ((Integer) v).doubleValue();
        }

        if (v instanceof Long) {
            return ((Long) v).doubleValue();
        }

        throw new IllegalArgumentException("Can't coerce '" + v + "' to double");
    }

    private static void assertExpression(String s, JsonLocation loc) throws YamlConverterException {
        if (s != null && s.startsWith("${") && s.endsWith("}")) {
            return;
        }

        throw new YamlConverterException("Invalid expression @ " + loc);
    }

    private static class ELCall implements Serializable {

        private final String expression;
        private final Set<VariableMapping> args;

        private ELCall(String expression, Set<VariableMapping> args) {
            this.expression = expression;
            this.args = args;
        }

    }

    private static class TypeInfo implements Serializable {

        public static TypeInfo parse(String s) {
            String type = s;
            Cardinality cardinality = Cardinality.ONE_AND_ONLY_ONE;

            if (s.endsWith("?")) {
                type = type.substring(0, type.length() - 1);
                cardinality = Cardinality.ONE_OR_NONE;
            } else if (s.endsWith("+")) {
                type = type.substring(0, type.length() - 1);
                cardinality = Cardinality.AT_LEAST_ONE;
            } else if (s.endsWith("*")) {
                type = type.substring(0, type.length() - 1);
                cardinality = Cardinality.ANY;
            }

            return new TypeInfo(type, cardinality);
        }

        private final String type;
        private final Cardinality cardinality;

        private TypeInfo(String type, Cardinality cardinality) {
            this.type = type;
            this.cardinality = cardinality;
        }
    }

    private YamlConverter() {
    }
}
