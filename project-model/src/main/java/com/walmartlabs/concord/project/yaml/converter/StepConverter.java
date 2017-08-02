package com.walmartlabs.concord.project.yaml.converter;

import com.fasterxml.jackson.core.JsonLocation;
import com.walmartlabs.concord.project.yaml.KV;
import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlStep;
import io.takari.bpm.model.BoundaryEvent;
import io.takari.bpm.model.SequenceFlow;
import io.takari.bpm.model.SourceMap;
import io.takari.bpm.model.SourceMap.Significance;
import io.takari.bpm.model.VariableMapping;
import io.takari.parc.Seq;

import java.io.Serializable;
import java.util.*;

public interface StepConverter<T extends YamlStep> {

    Chunk convert(ConverterContext ctx, T s) throws YamlConverterException;

    default void applyErrorBlock(ConverterContext ctx, Chunk c, String attachedRef, Map<String, Object> opts) throws YamlConverterException {
        if (opts == null) {
            return;
        }

        Seq<YamlStep> errorSteps = (Seq<YamlStep>) opts.get("error");
        if (errorSteps == null) {
            return;
        }

        String evId = ctx.nextId();
        c.addElement(new BoundaryEvent(evId, attachedRef, null));

        Chunk err = ctx.convert(errorSteps);

        // connect the boundary event to the error block's steps
        String dst = err.firstElement().getId();
        c.addElement(new SequenceFlow(ctx.nextId(), evId, dst));
        c.addElements(err.getElements());

        c.addOutputs(err.getOutputs());

        // keep the source map of the error block's steps
        c.addSourceMaps(err.getSourceMap());
    }

    default SourceMap toSourceMap(YamlStep step, String description) {
        JsonLocation l = step.getLocation();
        return new SourceMap(Significance.HIGH, String.valueOf(l.getSourceRef()), l.getLineNr(), l.getColumnNr(), description);
    }

    default Set<VariableMapping> getVarMap(Map<String, Object> options, String key) {
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

            Object v = deepConvert(kv.getValue());
            if (isExpression(v)) {
                sourceExpr = v.toString();
            } else {
                sourceValue = v;
            }

            result.add(new VariableMapping(null, sourceExpr, sourceValue, target, true));
        }

        return result;
    }

    static boolean isExpression(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof String)) {
            return false;
        }

        String s = (String) o;
        int i = s.indexOf("${");
        return i >= 0 && s.indexOf("}", i) > i;
    }

    @SuppressWarnings("unchecked")
    static Object deepConvert(Object o) {
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

    @SuppressWarnings("unchecked")
    default ELCall createELCall(String task, Object args) {
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

    class ELCall implements Serializable {

        private final String expression;
        private final Set<VariableMapping> args;

        private ELCall(String expression, Set<VariableMapping> args) {
            this.expression = expression;
            this.args = args;
        }

        public String getExpression() {
            return expression;
        }

        public Set<VariableMapping> getArgs() {
            return args;
        }
    }
}
