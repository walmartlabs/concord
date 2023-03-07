package com.walmartlabs.concord.runtime.v2.parser;

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

import com.walmartlabs.concord.forms.FormField.Cardinality;
import com.walmartlabs.concord.runtime.v2.exception.*;
import com.walmartlabs.concord.runtime.v2.model.FormField;
import com.walmartlabs.concord.runtime.v2.model.Location;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.walmartlabs.concord.forms.FormField.Option;
import static com.walmartlabs.concord.forms.FormFields.*;

public final class FormFieldParser {

    private static final String[] COMMON_ATTRS = new String[] {"label", "value", "allow", "type"};

    public static List<FormField> parse(Location location, List<Map<String, Map<String, Object>>> rawFields) {
        List<FormField> fields = new ArrayList<>();
        for (Map<String, Map<String, Object>> rf : rawFields) {
            if (rf.size() != 1) {
                throw new RuntimeException("Invalid dynamic form fields definition @ " + Location.toShortString(location) + ". Expected " + YamlValueType.ARRAY_OF_FORM_FIELD);
            }
            Map.Entry<String, Map<String, Object>> entry = rf.entrySet().iterator().next();

            fields.add(parse(entry.getKey(), location, YamlObjectConverter.from(entry.getValue(), location)));
        }
        return fields;
    }

    public static FormField parse(String name, Location location, YamlObject options) {
        try {
            return parseField(name, location, options);
        } catch (YamlProcessingException e) {
            throw new InvalidFieldDefinitionException(name, location, e);
        }
    }

    private static FormField parseField(String name, Location location, YamlObject optionsObject) {
        Map<String, YamlValue> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        values.putAll(optionsObject.getValues());
        YamlObject options = new YamlObject(values, optionsObject.getLocation());

        String label = options.getValue("label", YamlValueType.STRING);
        options.remove("label");

        Serializable defaultValue = options.remove("value");
        Serializable allowedValue = options.remove("allow");

        YamlValue typeValue = options.getYamlValue("type");
        if (typeValue == null) {
            throw new MandatoryFieldNotFoundException("type");
        }

        String type = typeValue.getValue(YamlValueType.STRING);
        options.remove("type");

        TypeInfo typeInfo = TypeInfo.parse(type);
        Map<String, Serializable> opts = convertOptions(typeInfo.type, typeValue.getLocation(), options);

        return FormField.builder()
                .name(name)
                .label(label)
                .type(typeInfo.type)
                .cardinality(typeInfo.cardinality)
                .defaultValue(defaultValue)
                .allowedValue(allowedValue)
                .location (location)
                .options(opts)
                .build();
    }

    private static Map<String, Serializable> convertOptions(String type, Location typeLocation, YamlObject options) {
        Map<String, Serializable> result = new LinkedHashMap<>();
        switch (type) {
            case StringField.TYPE: {
                processOption(StringField.PATTERN, options, YamlValueType.STRING, result);
                processOption(StringField.INPUT_TYPE, options, YamlValueType.STRING, result);
                processOption(CommonFieldOptions.READ_ONLY, options, YamlValueType.BOOLEAN, result);
                processOption(StringField.PLACEHOLDER, options, YamlValueType.STRING, result);
                processOption(StringField.SEARCH, options, YamlValueType.BOOLEAN, result);

                assertNoMoreOptions(options, StringField.PATTERN, StringField.INPUT_TYPE, CommonFieldOptions.READ_ONLY, StringField.PLACEHOLDER, StringField.SEARCH);

                break;
            }
            case IntegerField.TYPE: {
                processOption(IntegerField.MIN.name(), options, result, FormFieldParser::coerceToLong);
                processOption(IntegerField.MAX.name(), options, result, FormFieldParser::coerceToLong);
                processOption(CommonFieldOptions.READ_ONLY, options, YamlValueType.BOOLEAN, result);
                processOption(IntegerField.PLACEHOLDER, options, YamlValueType.STRING, result);

                assertNoMoreOptions(options, IntegerField.MIN, IntegerField.MAX, CommonFieldOptions.READ_ONLY, IntegerField.PLACEHOLDER);

                break;
            }
            case DecimalField.TYPE: {
                processOption(DecimalField.MIN.name(), options, result, FormFieldParser::coerceToDouble);
                processOption(DecimalField.MAX.name(), options, result, FormFieldParser::coerceToDouble);
                processOption(CommonFieldOptions.READ_ONLY, options, YamlValueType.BOOLEAN, result);
                processOption(DecimalField.PLACEHOLDER, options, YamlValueType.STRING, result);

                assertNoMoreOptions(options, DecimalField.MIN, DecimalField.MAX, CommonFieldOptions.READ_ONLY, DecimalField.PLACEHOLDER);

                break;
            }
            case BooleanField.TYPE:
                processOption(CommonFieldOptions.READ_ONLY, options, YamlValueType.BOOLEAN, result);

                assertNoMoreOptions(options, CommonFieldOptions.READ_ONLY);

                break;
            case FileField.TYPE: {
                assertNoMoreOptions(options);
                break;
            }
            case DateField.TYPE:
            case DateTimeField.TYPE: {
                processOption(DateFieldOptions.POPUP_POSITION, options, YamlValueType.STRING, result);

                assertNoMoreOptions(options, CommonFieldOptions.READ_ONLY, DateFieldOptions.POPUP_POSITION);

                break;
            }
            default:
                throw InvalidValueException.builder()
                        .location(typeLocation)
                        .actual(type)
                        .expected(StringField.TYPE, IntegerField.TYPE, DecimalField.TYPE,
                                BooleanField.TYPE, FileField.TYPE, DateField.TYPE, DateTimeField.TYPE)
                        .build();

        }

        return result;
    }

    private static void assertNoMoreOptions(YamlObject options, Option<?> ... expected) {
        if (options.isEmpty()) {
            return;
        }

        throw UnknownOptionException.builder()
                .location(options.getLocation())
                .unknown(options.values.entrySet().stream()
                        .map(kv -> UnknownOption.of(kv.getKey(), kv.getValue().getType(), kv.getValue().getLocation()))
                        .collect(Collectors.toList()))
                .expected(Stream.concat(Arrays.stream(COMMON_ATTRS), Arrays.stream(expected).map(Option::name)).collect(Collectors.toList()))
                .build();
    }

    private static <T extends Serializable> void processOption(String name, YamlObject options, Map<String, Serializable> result, Function<YamlValue, T> converter) {
        YamlValue v = options.getYamlValue(name);
        if (v != null) {
            result.put(name, converter.apply(v));
        }
        options.remove(name);
    }

    private static <T extends Serializable> void processOption(Option<?> option, YamlObject options, YamlValueType<T> type, Map<String, Serializable> result) {
        String name = option.name();
        T v = options.getValue(name, type);
        if (v != null) {
            result.put(name, v);
        }
        options.remove(name);
    }

    private static Long coerceToLong(YamlValue value) {
        Object v = value.getValue();
        if (v == null) {
            return null;
        }

        if (v instanceof Long) {
            return (Long) v;
        }

        if (v instanceof Integer) {
            return ((Integer) v).longValue();
        }

        throw InvalidValueTypeException.builder()
                .expected(YamlValueType.INT)
                .actual(value.getType())
                .location(value.getLocation())
                .build();
    }

    private static Double coerceToDouble(YamlValue value) {
        Object v = value.getValue();
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

        throw InvalidValueTypeException.builder()
                .expected(YamlValueType.FLOAT)
                .actual(value.getType())
                .location(value.getLocation())
                .build();
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

    private FormFieldParser() {
    }
}
