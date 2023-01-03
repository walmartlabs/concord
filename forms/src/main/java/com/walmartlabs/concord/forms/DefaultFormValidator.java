package com.walmartlabs.concord.forms;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import org.apache.commons.validator.routines.EmailValidator;

import java.util.*;

import static com.walmartlabs.concord.forms.FormField.Cardinality;
import static com.walmartlabs.concord.forms.FormFields.*;

public class DefaultFormValidator implements FormValidator {

    private final FormValidatorLocale locale;
    private final Collection<FieldValidator> validators;

    public DefaultFormValidator() {
        this(new DefaultFormValidatorLocale());
    }

    public DefaultFormValidator(FormValidatorLocale locale) {
        this.locale = locale;

        List<FieldValidator> vs = new ArrayList<>();
        vs.add(new StringFieldValidator(locale));
        vs.add(new IntegerFieldValidator(locale));
        vs.add(new DecimalFieldValidator(locale));
        vs.add(new BooleanFieldValidator(locale));
        vs.add(new FileFieldValidator());
        vs.add(new DateFieldValidator());

        this.validators = vs;
    }

    @Override
    public List<ValidationError> validate(Form form, Map<String, Object> data) {
        List<ValidationError> errors = new ArrayList<>();

        String formId = form.name();

        List<FormField> fields = form.fields();
        if (fields == null || fields.isEmpty()) {
            errors.add(ValidationError.of(ValidationError.GLOBAL_ERROR, locale.noFieldsDefined(formId)));
            return errors;
        }

        Map<String, Object> values = data;
        if (values == null) {
            values = Collections.emptyMap();
        }

        for (FormField f : fields) {
            Object v = values.get(f.name());
            Object allowed = f.allowedValue();

            ValidationError e = validate(formId, f, v, allowed);
            if (e != null) {
                errors.add(e);
            }
        }

        return errors;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ValidationError validate(String formId, FormField f, Object v, Object allowed) {
        String fieldName = f.name();

        Cardinality expectedCardinality = f.cardinality();

        if (!checkCardinality(v, expectedCardinality)) {
            return ValidationError.of(fieldName, locale.invalidCardinality(formId, f, v));
        }

        if (v == null) {
            return null;
        }

        v = box(v);
        allowed = box(allowed);

        if (v instanceof Collection) {
            Collection<Object> vs = (Collection<Object>) v;
            int idx = 0;
            for (Object vv : vs) {
                ValidationError e = validateSingleValue(formId, f, idx++, vv, allowed);
                if (e != null) {
                    return e;
                }
            }
        } else if (v instanceof Object[]) {
            Object[] vs = (Object[]) v;
            for (int i = 0; i < vs.length; i++) {
                ValidationError e = validateSingleValue(formId, f, i, vs[i], allowed);
                if (e != null) {
                    return e;
                }
            }
        } else {
            return validateSingleValue(formId, f, null, v, allowed);
        }

        return null;
    }

    private ValidationError validateSingleValue(String formId, FormField f, Integer idx, Object v, Object allowed) {
        String type = f.type();
        String fieldName = f.name();

        if (allowed != null) {
            if (!checkAllowedValue(v, allowed)) {
                return ValidationError.of(fieldName, locale.valueNotAllowed(formId, f, idx, allowed, v));
            }
        }

        boolean validated = false;
        for (FieldValidator validator : validators) {
            for (String t : validator.allowedTypes()) {
                if (type.equals(t)) {
                    ValidationError e = validator.validate(formId, f, idx, v);
                    if (e != null) {
                        return e;
                    }

                    validated = true;
                }
            }
        }

        if (!validated) {
            throw new RuntimeException("Unsupported form field type: " + type);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static boolean checkAllowedValue(Object v, Object allowed) {
        if (v.equals(allowed)) {
            return true;
        }

        if (allowed instanceof Collection) {
            Collection<Object> aa = (Collection<Object>) allowed;
            return aa.contains(v);
        } else if (allowed instanceof Object[]) {
            Object[] as = (Object[]) allowed;
            for (Object aa : as) {
                if (v.equals(aa)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean checkCardinality(Object v, FormField.Cardinality required) {
        Set<FormField.Cardinality> actual = guessCardinality(v);
        return actual.contains(required);
    }

    private static Set<FormField.Cardinality> guessCardinality(Object v) {
        int len = length(v);
        if (len < 0) {
            throw new IllegalArgumentException("Can't determine object's length: " + v);
        }

        if (len == 0) {
            return set(Cardinality.ANY, Cardinality.ONE_OR_NONE);
        } else if (len == 1) {
            return set(Cardinality.ANY, Cardinality.ONE_OR_NONE, Cardinality.ONE_AND_ONLY_ONE, Cardinality.AT_LEAST_ONE);
        } else {
            return set(Cardinality.ANY, Cardinality.AT_LEAST_ONE);
        }
    }

    private static Set<Cardinality> set(Cardinality... values) {
        Set<Cardinality> s = new HashSet<>(values.length);
        Collections.addAll(s, values);
        return s;
    }

    @SuppressWarnings("unchecked")
    private static int length(Object v) {
        if (v == null) {
            return 0;
        }

        if (v instanceof Collection) {
            return ((Collection<Object>) v).size();
        }

        if (v instanceof Object[]) {
            Object[] arr = (Object[]) v;
            return arr.length;
        }

        return 1;
    }

    private static boolean withinBounds(Object v, Long min, Long max) {
        if (min == null && max == null) {
            return true;
        }

        boolean low = true, high = true;

        if (v instanceof Long) {
            Long l = (Long) v;
            if (min != null) {
                low = l >= min;
            }
            if (max != null) {
                high = l <= max;
            }
        } else if (v instanceof Integer) {
            int i = (Integer) v;
            if (min != null) {
                if (!validInt(min)) {
                    throw new IllegalArgumentException("Invalid integer min bound: " + min + ", use long values if needed");
                }
                low = i >= min.intValue();
            }
            if (max != null) {
                if (!validInt(max)) {
                    throw new IllegalArgumentException("Invalid integer max bound: " + max + ", use long values if needed");
                }
                high = i <= max.intValue();
            }
        } else {
            throw new IllegalArgumentException("Unsupported integer type: " + v.getClass());
        }

        return low && high;
    }

    private static boolean withinBounds(Object v, Double min, Double max) {
        if (min == null && max == null) {
            return true;
        }

        boolean low = true, high = true;

        if (v instanceof Double) {
            Double l = (Double) v;
            if (min != null) {
                low = l >= min;
            }
            if (max != null) {
                high = l <= max;
            }
        } else if (v instanceof Float) {
            float i = (Float) v;
            if (min != null) {
                if (!validFloat(min)) {
                    throw new IllegalArgumentException("Invalid float min bound: " + min + ", use double values if needed");
                }
                low = i >= min.floatValue();
            }
            if (max != null) {
                if (!validFloat(max)) {
                    throw new IllegalArgumentException("Invalid float max bound: " + max + ", use double values if needed");
                }
                high = i <= max.floatValue();
            }
        } else {
            throw new IllegalArgumentException("Unsupported decimal type: " + v.getClass());
        }

        return low && high;
    }

    private static boolean validInt(Long l) {
        return l <= Integer.MAX_VALUE && l >= Integer.MIN_VALUE;
    }

    private static boolean validFloat(Double d) {
        return d <= Float.MAX_VALUE && d >= Float.MIN_VALUE;
    }

    private static Object box(Object v) {
        if (v == null) {
            return null;
        }

        if (v instanceof int[]) {
            int[] is = (int[]) v;

            Object[] os = new Object[is.length];
            for (int i = 0; i < is.length; i++) {
                os[i] = is[i];
            }

            return os;
        }

        if (v instanceof long[]) {
            long[] is = (long[]) v;

            Object[] os = new Object[is.length];
            for (int i = 0; i < is.length; i++) {
                os[i] = is[i];
            }

            return os;
        }

        if (v instanceof float[]) {
            float[] is = (float[]) v;

            Object[] os = new Object[is.length];
            for (int i = 0; i < is.length; i++) {
                os[i] = is[i];
            }

            return os;
        }

        if (v instanceof double[]) {
            double[] is = (double[]) v;

            Object[] os = new Object[is.length];
            for (int i = 0; i < is.length; i++) {
                os[i] = is[i];
            }

            return os;
        }

        if (v instanceof boolean[]) {
            boolean[] is = (boolean[]) v;

            Object[] os = new Object[is.length];
            for (int i = 0; i < is.length; i++) {
                os[i] = is[i];
            }

            return os;
        }

        if (v instanceof char[]) {
            char[] is = (char[]) v;

            Object[] os = new Object[is.length];
            for (int i = 0; i < is.length; i++) {
                os[i] = is[i];
            }

            return os;
        }

        return v;
    }

    public interface FieldValidator {

        /**
         * @return field types that are supported by this validator.
         */
        String[] allowedTypes();

        /**
         * Validate a single value of a field.
         *
         * @param formId ID of a form.
         * @param f      the validated field
         * @param idx    index of the value if the field is a collection.
         * @param v      the value of the field.
         * @return validation error or {@code null} if valid.
         */
        ValidationError validate(String formId, FormField f, Integer idx, Object v);
    }

    public static final class IntegerFieldValidator implements FieldValidator {

        private static final String[] TYPES = {IntegerField.TYPE};

        private final FormValidatorLocale locale;

        public IntegerFieldValidator(FormValidatorLocale locale) {
            this.locale = locale;
        }

        @Override
        public String[] allowedTypes() {
            return TYPES;
        }

        @Override
        public ValidationError validate(String formId, FormField f, Integer idx, Object v) {
            String fieldName = f.name();

            if (v instanceof Integer || v instanceof Long) {
                Long min = f.getOption(IntegerField.MIN);
                Long max = f.getOption(IntegerField.MAX);

                if (!withinBounds(v, min, max)) {
                    return ValidationError.of(fieldName, locale.integerRangeError(formId, f, idx, min, max, v));
                }
            } else {
                return ValidationError.of(fieldName, locale.expectedInteger(formId, f, idx, v));
            }

            return null;
        }
    }

    public static final class DecimalFieldValidator implements FieldValidator {

        private static final String[] TYPES = {DecimalField.TYPE};

        private final FormValidatorLocale locale;

        public DecimalFieldValidator(FormValidatorLocale locale) {
            this.locale = locale;
        }

        @Override
        public String[] allowedTypes() {
            return TYPES;
        }

        @Override
        public ValidationError validate(String formId, FormField f, Integer idx, Object v) {
            String fieldName = f.name();

            if (v instanceof Double || v instanceof Float) {
                Double min = f.getOption(DecimalField.MIN);
                Double max = f.getOption(DecimalField.MAX);

                if (!withinBounds(v, min, max)) {
                    return ValidationError.of(fieldName, locale.decimalRangeError(formId, f, idx, min, max, v));
                }
            } else {
                return ValidationError.of(fieldName, locale.expectedDecimal(formId, f, idx, v));
            }

            return null;
        }
    }

    public static final class BooleanFieldValidator implements FieldValidator {

        private static final String[] TYPES = {BooleanField.TYPE};

        private final FormValidatorLocale locale;

        public BooleanFieldValidator(FormValidatorLocale locale) {
            this.locale = locale;
        }

        @Override
        public String[] allowedTypes() {
            return TYPES;
        }

        @Override
        public ValidationError validate(String formId, FormField f, Integer idx, Object v) {
            String fieldName = f.name();

            if (!(v instanceof Boolean)) {
                return ValidationError.of(fieldName, locale.expectedBoolean(formId, f, idx, v));
            }

            return null;
        }
    }

    public static final class DateFieldValidator implements DefaultFormValidator.FieldValidator {

        private static final String[] TYPES = {DateField.TYPE, DateTimeField.TYPE};

        @Override
        public String[] allowedTypes() {
            return TYPES;
        }

        @Override
        public ValidationError validate(String formId, FormField f, Integer idx, Object v) {
            if (!(v instanceof String)) {
                throw new IllegalArgumentException("Expected a date value: " + f.name());
            }

            return null;
        }
    }

    public static final class FileFieldValidator implements DefaultFormValidator.FieldValidator {

        private static final String[] TYPES = {FileField.TYPE};

        @Override
        public String[] allowedTypes() {
            return TYPES;
        }

        @Override
        public ValidationError validate(String formId, FormField f, Integer idx, Object v) {
            String fieldName = f.name();

            if (!(v instanceof String)) {
                throw new IllegalArgumentException("Expected a file value: " + fieldName);
            }

            return null;
        }
    }

    public static final class StringFieldValidator implements FieldValidator {

        private static final String[] TYPES = {StringField.TYPE};

        private final FormValidatorLocale locale;

        public StringFieldValidator(FormValidatorLocale locale) {
            this.locale = locale;
        }

        @Override
        public String[] allowedTypes() {
            return TYPES;
        }

        @Override
        public ValidationError validate(String formId, FormField f, Integer idx, Object v) {
            String fieldName = f.name();

            if (v instanceof String) {
                String pattern = f.getOption(StringField.PATTERN);
                if (pattern == null) {
                    return null;
                }

                String sv = (String) v;
                if (!sv.matches(pattern)) {
                    return ValidationError.of(fieldName, locale.doesntMatchPattern(formId, f, idx, pattern, v));
                }
            } else {
                return ValidationError.of(fieldName, locale.expectedString(formId, f, idx, v));
            }

            String inputType = f.getOption(StringField.INPUT_TYPE);
            if ("email".equalsIgnoreCase(inputType)) {
                boolean valid = EmailValidator.getInstance().isValid((String)v);
                if (!valid) {
                    return ValidationError.of(f.name(), "Invalid email address");
                }
            }

            return null;
        }
    }
}
