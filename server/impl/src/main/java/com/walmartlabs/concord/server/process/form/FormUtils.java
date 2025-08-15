package com.walmartlabs.concord.server.process.form;

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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.common.form.ConcordFormFields;
import com.walmartlabs.concord.common.form.ConcordFormValidatorLocale;
import com.walmartlabs.concord.forms.ValidationError;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import io.takari.bpm.form.Form;
import io.takari.bpm.model.form.DefaultFormFields;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.bpm.model.form.FormField;

import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.walmartlabs.concord.common.form.ConcordFormFields.FieldOptions.READ_ONLY;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public final class FormUtils {

    /**
     * Date/time format used to pass date and dateTime fields between the Server and the process.
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US);

    /**
     * All date/time values are converted into the default time zone.
     */
    private static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("UTC");

    public static Map<String, String> mergeErrors(List<ValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }

        // TODO merge multiple errors
        Map<String, String> m = new HashMap<>();
        for (ValidationError e : errors) {
            m.put(e.fieldName(), e.error());
        }
        return m;
    }

    public static Map<String, Object> convert(MultivaluedMap<String, String> data) {
        Map<String, Object> m = new HashMap<>();
        if (data != null) {
            data.forEach((k, v) -> {
                if (v == null) {
                    return;
                }

                int size = v.size();
                if (size == 0) {
                    return;
                }

                if (size == 1) {
                    m.put(k, v.get(0));
                } else {
                    m.put(k, v);
                }
            });
        }
        return m;
    }

    // TODO this probably should be a part of the bpm engine's FormService
    public static Map<String, Object> convert(ConcordFormValidatorLocale locale, Form form, Map<String, Object> m) throws ValidationException {
        FormDefinition fd = form.getFormDefinition();

        Map<String, String> tmpFiles = new HashMap<>();
        Map<String, Object> m2 = new HashMap<>();
        m2.put(Constants.Files.FORM_FILES, tmpFiles);

        Map<String, Object> defaultData = FormUtils.values(form);

        for (FormField f : fd.getFields()) {
            String k = f.getName();

            Object v = Boolean.TRUE.equals(f.getOption(READ_ONLY)) ? defaultData.get(k) : m.get(k);

            boolean isOptional = f.getCardinality() == FormField.Cardinality.ONE_OR_NONE || f.getCardinality() == FormField.Cardinality.ANY;
            if (v == null && !isOptional) {
                Map<String, Object> allowedValues = form.getAllowedValues();
                if (allowedValues == null) {
                    allowedValues = Collections.emptyMap();
                }

                v = allowedValueAsFieldValue(allowedValues.get(f.getName()));
            }

            /*
             * Use cardinality as an indicator to convert single value (coming as a string) into an array
             * for the scenario when only one value was provided by the user
             */
            if (v instanceof String && (f.getCardinality() == FormField.Cardinality.ANY || f.getCardinality() == FormField.Cardinality.AT_LEAST_ONE)) {
                v = Collections.singletonList(v);
            }

            v = convert(locale, fd.getName(), f, null, v);

            if (v == null && !isOptional) {
                continue;
            }

            if (v != null && f.getType().equals(ConcordFormFields.FileField.TYPE)) {
                String wsFileName = "_form_files/" + form.getFormDefinition().getName() + "/" + f.getName();
                tmpFiles.put(wsFileName, (String) v);
                m2.put(k, wsFileName);
            } else {
                m2.put(k, v);
            }
        }
        return m2;
    }

    public static Map<String, Object> values(Form form) {
        String formName = form.getFormDefinition().getName();

        Map<String, Object> env = form.getEnv();
        if (env == null) {
            env = Collections.emptyMap();
        }

        Map<String, Object> formState = MapUtils.getMap(env, formName, Collections.emptyMap());
        Map<String, Object> extraValues = extraValues(form);

        // merge the initial form values and the "extra" values, provided
        // in the "values" option of the form
        Map<String, Object> a = new HashMap<>(formState);
        Map<String, Object> b = new HashMap<>(extraValues);
        ConfigurationUtils.merge(a, b);
        return a;
    }

    public static Map<String, Object> extraValues(Form form) {
        return MapUtils.getMap(form.getOptions(), "values", Collections.emptyMap());
    }

    private static Object convert(ConcordFormValidatorLocale locale, String formName, FormField f, Integer idx, Object v) throws ValidationException {
        if (v instanceof String) {
            String s = (String) v;

            switch (f.getType()) {
                case DefaultFormFields.StringField.TYPE: {
                    if (s.isEmpty()) {
                        return null;
                    }
                    break;
                }
                case DefaultFormFields.IntegerField.TYPE: {
                    if (s.isEmpty()) {
                        return null;
                    }

                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        throw new ValidationException(f, s, locale.expectedInteger(formName, f, idx, s));
                    }
                }
                case DefaultFormFields.DecimalField.TYPE: {
                    if (s.isEmpty()) {
                        return null;
                    }

                    try {
                        return Double.parseDouble(s);
                    } catch (NumberFormatException e) {
                        throw new ValidationException(f, s, locale.expectedDecimal(formName, f, idx, s));
                    }
                }
                case DefaultFormFields.BooleanField.TYPE: {
                    if (s.isEmpty()) {
                        // default HTML checkbox will be submitted as an empty value if checked
                        return true;
                    }
                    return Boolean.parseBoolean(s);
                }
                case ConcordFormFields.FileField.TYPE: {
                    try {
                        Path tmp = PathUtils.createTempFile(f.getName(), ".tmp");
                        Files.write(tmp, s.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
                        return tmp.toString();
                    } catch (IOException e) {
                        throw new ConcordApplicationException("Error reading file for form field '" + f.getName() + "'", e);
                    }
                }
                case ConcordFormFields.DateField.TYPE:
                case ConcordFormFields.DateTimeField.TYPE: {
                    if (s.isEmpty()) {
                        return null;
                    }

                    // adjust the value to the default system timezone
                    // on the process level those values are represented as java.util.Date (i.e. no TZ info retained)
                    // so we assume all Date values are in the default system TZ (which is typically UTC)
                    return ZonedDateTime.parse(s)
                            .withZoneSameInstant(DEFAULT_TIME_ZONE)
                            .format(DATE_TIME_FORMATTER);
                }
            }
        } else if (v instanceof List) {
            List<?> l = (List<?>) v;
            if (l.isEmpty()) {
                return null;
            }

            List<Object> ll = new ArrayList<>(l.size());
            int i = 0;
            for (Object o : l) {
                ll.add(convert(locale, formName, f, i, o));
                i++;
            }
            return ll;
        } else if (v instanceof InputStream) {
            if (f.getType().equals(ConcordFormFields.FileField.TYPE)) {
                try (InputStream is = (InputStream) v) {
                    Path tmp = PathUtils.createTempFile(f.getName(), ".tmp");
                    Files.copy(is, tmp, REPLACE_EXISTING);
                    return tmp.toString();
                } catch (IOException e) {
                    throw new ConcordApplicationException("Error reading file for form field '" + f.getName() + "'", e);
                }
            }
        } else if (v == null) {
            if (f.getType().equals(DefaultFormFields.BooleanField.TYPE)) {
                return false;
            }
        }

        return v;
    }

    private static Object allowedValueAsFieldValue(Object allowedValue) {
        if (allowedValue instanceof Collection) {
            if (((Collection<?>) allowedValue).size() == 1) {
                return ((Collection<?>) allowedValue).iterator().next();
            }

            return null;
        }

        return allowedValue;
    }

    public static class ValidationException extends Exception {

        private static final long serialVersionUID = 1L;

        private final FormField field;
        private final String input;
        private final String message;

        private ValidationException(FormField field, String input, String message) {
            this.field = field;
            this.input = input;
            this.message = message;
        }

        public FormField getField() {
            return field;
        }

        public String getInput() {
            return input;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    private FormUtils() {
    }
}
