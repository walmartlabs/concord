package com.walmartlabs.concord.project.yaml;

import com.fasterxml.jackson.core.JsonLocation;
import com.walmartlabs.concord.project.yaml.model.YamlFormDefinition;
import com.walmartlabs.concord.project.yaml.model.YamlFormField;
import io.takari.bpm.model.form.DefaultFormFields.DecimalField;
import io.takari.bpm.model.form.DefaultFormFields.IntegerField;
import io.takari.bpm.model.form.DefaultFormFields.StringField;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.bpm.model.form.FormField;
import io.takari.bpm.model.form.FormField.Cardinality;
import io.takari.bpm.model.form.FormField.Option;
import io.takari.parc.Seq;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class YamlFormConverter {

    private static final String OPTIONS_FIELD_NAME = "_options";

    public static FormDefinition convert(String name, List<YamlFormField> fields) throws YamlConverterException {
        Map<String, Object> options = null;

        List<FormField> l = new ArrayList<>();
        for (YamlFormField f : fields) {
            if (OPTIONS_FIELD_NAME.equals(f.getName())) {
                if (options != null) {
                    throw new YamlConverterException("Duplicate options definition in form '" + name + "'");
                }

                options = f.getOptions();
                continue;
            }

            l.add(convert(f));
        }
        return new FormDefinition(name, l);
    }

    public static FormDefinition convert(YamlFormDefinition def) throws YamlConverterException {
        return convert(def.getName(), def.getFields().toList());
    }

    private static FormField convert(YamlFormField f) throws YamlConverterException {
        Map<String, Object> opts = f.getOptions();

        // common parameters
        String label = (String) opts.remove("label");
        Object defaultValue = box(opts.remove("value"));
        Object allowedValue = box(opts.remove("allow"));

        // type-specific options
        Map<Option<?>, Object> options = new HashMap<>();

        TypeInfo tInfo = parseType(opts.remove("type"), f.getLocation());
        switch (tInfo.type) {
            case StringField.TYPE: {
                options.put(StringField.PATTERN, opts.remove("pattern"));
                break;
            }
            case IntegerField.TYPE: {
                options.put(IntegerField.MIN, coerceToLong(opts.remove("min")));
                options.put(IntegerField.MAX, coerceToLong(opts.remove("max")));
                break;
            }
            case DecimalField.TYPE: {
                options.put(DecimalField.MIN, coerceToDouble(opts.remove("min")));
                options.put(DecimalField.MAX, coerceToDouble(opts.remove("max")));
                break;
            }
            default:
                throw new YamlConverterException("Unknown field type: " + tInfo.type + " @ " + f.getLocation());
        }

        if (!opts.isEmpty()) {
            throw new YamlConverterException("Unknown field options: " + opts.keySet() + " @ " + f.getLocation());
        }

        return new FormField.Builder(f.getName(), tInfo.type)
                .label(label)
                .defaultValue(defaultValue)
                .allowedValue(allowedValue)
                .cardinality(tInfo.cardinality)
                .options(options)
                .build();
    }

    private static TypeInfo parseType(Object t, JsonLocation loc) throws YamlConverterException {
        if (!(t instanceof String)) {
            throw new YamlConverterException("Expected a field type @ " + loc);
        }
        return TypeInfo.parse((String) t);
    }

    private static Object box(Object v) {
        if (v instanceof Seq) {
            return ((Seq) v).toList();
        }
        return v;
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

    private YamlFormConverter() {
    }
}
