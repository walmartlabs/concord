package com.walmartlabs.concord.project.yaml;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.fasterxml.jackson.core.JsonLocation;
import com.walmartlabs.concord.common.form.ConcordFormFields;
import com.walmartlabs.concord.project.yaml.model.YamlFormDefinition;
import com.walmartlabs.concord.project.yaml.model.YamlFormField;
import io.takari.bpm.model.form.DefaultFormFields.BooleanField;
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

import static com.walmartlabs.concord.common.form.ConcordFormFields.FieldOptions.*;

public final class YamlFormConverter {

    private static final String OPTIONS_FIELD_NAME = "_options";

    private static final Map<String, Option<?>> PASSTHROUGH_OPTIONS = createPassthroughOptions();

    public static FormDefinition convert(String name, List<YamlFormField> fields) throws YamlConverterException {
        Map<String, Object> options = null;

        List<FormField> l = new ArrayList<>();
        for (YamlFormField f : fields) {
            if (f == null || f.getName() == null) {
                throw new YamlConverterException("Empty field definition in form '" + name + "'");
            }

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
        return convert(f.getName(), f.getOptions(), f.getLocation());
    }

    public static FormField convert(String name, Map<String, Object> opts, JsonLocation loc) throws YamlConverterException {
        // common parameters
        String label = (String) opts.remove("label");
        Object defaultValue = box(opts.remove("value"));
        Object allowedValue = box(opts.remove("allow"));

        // type-specific options
        Map<Option<?>, Object> options = new HashMap<>();

        TypeInfo tInfo = parseType(opts.remove("type"), loc);
        switch (tInfo.type) {
            case StringField.TYPE: {
                options.put(StringField.PATTERN, opts.remove("pattern"));
                options.put(INPUT_TYPE, opts.remove("inputType"));
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
            case BooleanField.TYPE: {
                break;
            }
            case ConcordFormFields.FileField.TYPE: {
                break;
            }
            case ConcordFormFields.DateField.TYPE:
            case ConcordFormFields.DateTimeField.TYPE: {
                options.put(ConcordFormFields.DateFieldOptions.POPUP_POSITION, opts.remove("popupPosition"));
                break;
            }
            default:
                throw new YamlConverterException("Unknown field type: " + tInfo.type + (loc != null ? "@ " + loc : ""));
        }

        PASSTHROUGH_OPTIONS.forEach((k, option) -> {
            Object v = opts.remove(k);
            if (v != null) {
                options.put(option, v);
            }
        });

        if (!opts.isEmpty()) {
            throw new YamlConverterException("Unknown field options: " + opts.keySet() + (loc != null ? "@ " + loc : ""));
        }

        return new FormField.Builder(name, tInfo.type)
                .label(label)
                .defaultValue(defaultValue)
                .allowedValue(allowedValue)
                .cardinality(tInfo.cardinality)
                .options(options)
                .build();
    }

    private static TypeInfo parseType(Object t, JsonLocation loc) throws YamlConverterException {
        if (!(t instanceof String)) {
            throw new YamlConverterException("Expected a field type" + (loc != null ? "@ " + loc : ""));
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

    private static Map<String, Option<?>> createPassthroughOptions() {
        Map<String, Option<?>> result = new HashMap<>();
        result.put("placeholder", PLACEHOLDER);
        result.put("readonly", READ_ONLY);
        result.put("search", SEARCH);
        return result;
    }

    private static class TypeInfo implements Serializable {

        private static final long serialVersionUID = 1L;

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
