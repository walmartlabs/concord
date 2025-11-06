package com.walmartlabs.concord.forms;

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

import com.walmartlabs.concord.common.PathUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.forms.FormFields.CommonFieldOptions.READ_ONLY;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public final class FormUtils {

    private static final String RUN_AS_USERNAME_PATH = Constants.RUN_AS_KEY + "." +
            Constants.RUN_AS_USERNAME_KEY;

    private static final String RUN_AS_LDAP_GROUP_PATH = Constants.RUN_AS_KEY + "." +
            Constants.RUN_AS_LDAP_KEY + "." +
            Constants.RUN_AS_GROUP_KEY;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);

    @SuppressWarnings("unchecked")
    public static Set<String> getRunAsUsers(String formName, Map<String, Serializable> runAsParams) {
        if (runAsParams == null) {
            return Collections.emptySet();
        }

        Object v = runAsParams.get(Constants.RUN_AS_USERNAME_KEY);
        if (v == null) {
            return Collections.emptySet();
        }

        if (v instanceof String) {
            return Collections.singleton((String) v);
        } else if (v instanceof Collection) {
            Collection<Object> items = (Collection<Object>) v;

            Set<String> result = new HashSet<>();
            for (Object item : items) {
                if (item instanceof String) {
                    result.add((String) item);
                } else {
                    throw new RuntimeException("Expected a string or a list of strings value, got: " + item + ". " +
                            "Check the '" + RUN_AS_USERNAME_PATH + "' parameter of '" + formName + "' form definition.");
                }
            }
            return result;
        }

        throw new RuntimeException("Invalid form definition: " + formName + ". " +
                "Expected a username definition, got: " + v);
    }

    /**
     * Returns a collection of LDAP groups that can submit the form or an empty collection if no restrictions are
     * specified.
     * <p>
     * This method takes care of all our different ways to specify the form's LDAP groups.
     * Because form options are evaluated at the runtime, we can't transform all syntax variants into a single one
     * on the YAML parsing phase, we need to coerce the data at the runtime.
     * <p>
     * <h2>Supported syntax variants</h2>
     * <p>
     * The original, single value:
     * <pre>
     * runAs:
     *   ldap:
     *     group: "aGroupName"
     * </pre>
     * <p>
     * The updated, multi value:
     * <pre>
     * runAs:
     *   ldap:
     *     - group: "aGroupName"
     *     - group: "bGroupName"
     * </pre>
     * <p>
     * The recommended multi value:
     * <pre>
     * runAs:
     *   ldap:
     *     group:
     *     - "aGroupName"
     *     - "bGroupName"
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public static Set<String> getRunAsLdapGroups(String formName, Map<String, Serializable> runAsParams) {
        if (runAsParams == null) {
            return Collections.emptySet();
        }

        Object ldap = runAsParams.get(Constants.RUN_AS_LDAP_KEY);
        if (ldap == null) {
            return Collections.emptySet();
        }

        // the recommended syntax
        if (ldap instanceof Map) {
            // ldap:
            //   group: VALUE
            Map<Object, Object> m = (Map<Object, Object>) ldap;
            final Object value = m.get(Constants.RUN_AS_GROUP_KEY);

            // a single string value
            if (value instanceof String) {
                return Collections.singleton((String) value);
            }

            // a collection of strings (or something else, but that would be an error)
            if (value instanceof Collection) {
                Collection<Object> items = (Collection<Object>) value;

                Set<String> result = new HashSet<>();
                for (Object i : items) {
                    if (i instanceof String) {
                        result.add((String) i);
                    } else {
                        throw invalidLdapGroupElement(formName, RUN_AS_LDAP_GROUP_PATH, i);
                    }
                }
                return result;
            }
        }

        if (ldap instanceof Collection) {
            // ldap:
            // - VALUE
            // - VALUE
            Collection<Object> items = (Collection<Object>) ldap;
            return items.stream()
                    .map(o -> parseOldGroupDefinition(formName, o))
                    .collect(Collectors.toSet());
        }

        throw new RuntimeException("Invalid form definition: " + formName + ". " +
                "Expected a LDAP group definition, got: " + ldap);
    }

    @SuppressWarnings("unchecked")
    private static String parseOldGroupDefinition(String formName, Object item) {
        if (item instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) item;
            Object o = m.get(Constants.RUN_AS_GROUP_KEY);
            if (o instanceof String) {
                return (String) o;
            } else {
                throw invalidLdapGroupElement(formName, RUN_AS_LDAP_GROUP_PATH, o);
            }
        }

        throw invalidLdapGroupElement(formName, RUN_AS_LDAP_GROUP_PATH, item);
    }

    private static RuntimeException invalidLdapGroupElement(String formName, String k, Object v) {
        return new RuntimeException("Expected a string value or a group definition, " +
                "got: " + v + ". Check the '" + k + "' parameter of '" + formName + "' form definition");
    }

    public static Map<String, Object> convert(FormValidatorLocale locale, Form form, Map<String, Object> m) throws ValidationException {
        if (m == null) {
            return Collections.emptyMap();
        }

        Map<String, String> tmpFiles = new HashMap<>();
        Map<String, Object> m2 = new HashMap<>();
        m2.put(Constants.FORM_FILES, tmpFiles);

        for (FormField f : form.fields()) {
            String k = f.name();

            Object v = Boolean.TRUE.equals(f.getOption(READ_ONLY)) ? f.defaultValue() : m.getOrDefault(k, f.defaultValue());

            boolean isOptional = f.cardinality() == FormField.Cardinality.ONE_OR_NONE || f.cardinality() == FormField.Cardinality.ANY;
            if (v == null && !isOptional) {
                v = allowedValueAsFieldValue(f.allowedValue());
            }

            /*
             * Use cardinality as an indicator to convert single value (coming as a string) into an array
             * for the scenario when only one value was provided by the user
             */
            if (v instanceof String && (f.cardinality() == FormField.Cardinality.ANY || f.cardinality() == FormField.Cardinality.AT_LEAST_ONE)) {
                v = Collections.singletonList(v);
            }

            v = convert(locale, form.name(), f, null, v);

            if (v == null && !isOptional) {
                continue;
            }

            if (v != null && f.type().equals(FormFields.FileField.TYPE)) {
                String wsFileName = "_form_files/" + form.name() + "/" + f.name();
                tmpFiles.put(wsFileName, (String) v);
                m2.put(k, wsFileName);
            } else {
                m2.put(k, v);
            }
        }
        return m2;
    }

    public static Object convert(FormValidatorLocale locale, String formName, FormField f, Integer idx, Object v) throws ValidationException {
        if (v instanceof String) {
            String s = (String) v;

            switch (f.type()) {
                case FormFields.StringField.TYPE: {
                    if (s.isEmpty()) {
                        return null;
                    }
                    break;
                }
                case FormFields.IntegerField.TYPE: {
                    if (s.isEmpty()) {
                        return null;
                    }

                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        throw new ValidationException(f, s, locale.expectedInteger(formName, f, idx, s));
                    }
                }
                case FormFields.DecimalField.TYPE: {
                    if (s.isEmpty()) {
                        return null;
                    }

                    try {
                        return Double.parseDouble(s);
                    } catch (NumberFormatException e) {
                        throw new ValidationException(f, s, locale.expectedDecimal(formName, f, idx, s));
                    }
                }
                case FormFields.BooleanField.TYPE: {
                    if (s.isEmpty()) {
                        // default HTML checkbox will be submitted as an empty value if checked
                        return true;
                    }
                    return Boolean.parseBoolean(s);
                }
                case FormFields.FileField.TYPE: {
                    try {
                        Path tmp = PathUtils.createTempFile(f.name(), ".tmp");
                        Files.write(tmp, s.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
                        return tmp.toString();
                    } catch (IOException e) {
                        throw new RuntimeException("Error reading file for form field '" + f.name() + "'", e);
                    }
                }
                case FormFields.DateField.TYPE:
                case FormFields.DateTimeField.TYPE: {
                    if (s.isEmpty()) {
                        return null;
                    }

                    // adjust the value to the default system timezone
                    // on the process level those values are represented as java.util.Date (i.e. no TZ info retained)
                    // so we assume all Date values are in the default system TZ (which is typically UTC)
                    return ZonedDateTime.parse(s)
                            .withZoneSameInstant(ZoneId.systemDefault())
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
            if (f.type().equals(FormFields.FileField.TYPE)) {
                try (InputStream is = (InputStream) v) {
                    Path tmp = PathUtils.createTempFile(f.name(), ".tmp");
                    Files.copy(is, tmp, REPLACE_EXISTING);
                    return tmp.toString();
                } catch (IOException e) {
                    throw new RuntimeException("Error reading file for form field '" + f.name() + "'", e);
                }
            }
        } else if (v == null) {
            if (f.type().equals(FormFields.BooleanField.TYPE)) {
                return false;
            }
        }

        return v;
    }

    private static Object allowedValueAsFieldValue(Serializable allowedValue) {
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
