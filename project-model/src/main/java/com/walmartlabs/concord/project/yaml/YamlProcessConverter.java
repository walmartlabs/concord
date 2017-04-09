package com.walmartlabs.concord.project.yaml;

import com.fasterxml.jackson.core.JsonLocation;
import com.walmartlabs.concord.project.yaml.model.*;
import io.takari.bpm.model.*;
import io.takari.bpm.model.ProcessDefinitionBuilder.Fork;
import io.takari.bpm.model.form.FormExtension;
import io.takari.parc.Seq;

import java.io.Serializable;
import java.util.*;

public final class YamlProcessConverter {

    public static ProcessDefinition convert(String name, List<YamlStep> steps) throws YamlConverterException {
        ProcessDefinitionBuilder.Process proc = ProcessDefinitionBuilder.newProcess(name);
        apply(proc, steps);
        return proc.end();
    }

    public static ProcessDefinition convert(YamlProcessDefinition def) throws YamlConverterException {
        ProcessDefinitionBuilder.Process proc = ProcessDefinitionBuilder.newProcess(def.getName());
        apply(proc, def.getSteps());
        return proc.end();
    }

    private static ProcessDefinitionBuilder.Seq apply(ProcessDefinitionBuilder.Seq proc, List<YamlStep> steps) throws YamlConverterException {
        for (YamlStep s : steps) {
            proc = apply(proc, s);
        }
        return proc;
    }

    private static ProcessDefinitionBuilder.Seq apply(ProcessDefinitionBuilder.Seq proc, Seq<YamlStep> steps) throws YamlConverterException {
        return apply(proc, steps.toList());
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
            return proc.userTask(Collections.singletonList(new FormExtension(c.getKey())));
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

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
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

    private static class ELCall implements Serializable {

        private final String expression;
        private final Set<VariableMapping> args;

        private ELCall(String expression, Set<VariableMapping> args) {
            this.expression = expression;
            this.args = args;
        }

    }

    private YamlProcessConverter() {
    }
}
