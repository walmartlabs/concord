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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.form.ConcordFormFields;
import com.walmartlabs.concord.common.form.ConcordFormValidatorLocale;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import io.takari.bpm.form.Form;
import io.takari.bpm.model.form.DefaultFormFields;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.bpm.model.form.FormField;
import org.sonatype.siesta.ValidationErrorsException;

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
import java.util.stream.Collectors;

import static com.walmartlabs.concord.project.InternalConstants.Files.FORM_FILES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public final class FormUtils {

    private static final String RUN_AS_USERNAME_PATH = InternalConstants.Forms.RUN_AS_KEY + "." +
            InternalConstants.Forms.RUN_AS_USERNAME_KEY;

    private static final String RUN_AS_LDAP_GROUP_PATH = InternalConstants.Forms.RUN_AS_KEY + "." +
            InternalConstants.Forms.RUN_AS_LDAP_KEY + "." +
            InternalConstants.Forms.RUN_AS_GROUP_KEY;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("YYYY-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);

    /**
     * Returns the name of the users which can submit the form or {@code null} if no restrictions are specified.
     */
    public static String getRunAsUser(String formName, Map<String, Object> runAsParams) {
        if (runAsParams == null) {
            return null;
        }

        Object v = runAsParams.get(InternalConstants.Forms.RUN_AS_USERNAME_KEY);
        if (v == null) {
            return null;
        }

        if (!(v instanceof String)) {
            throw new ValidationErrorsException("Expected a string value, got: " + v + ". " +
                    "Check the '" + RUN_AS_USERNAME_PATH + "' parameter of '" + formName + "' form definition.");
        }

        return (String) v;
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
     * The recommended multivalue:
     * <pre>
     * runAs:
     *   ldap:
     *     group:
     *     - "aGroupName"
     *     - "bGroupName"
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public static Set<String> getRunAsLdapGroups(String formName, Map<String, Object> runAsParams) {
        if (runAsParams == null) {
            return Collections.emptySet();
        }

        Object ldap = runAsParams.get(InternalConstants.Forms.RUN_AS_LDAP_KEY);
        if (ldap == null) {
            return Collections.emptySet();
        }

        // the recommended syntax
        if (ldap instanceof Map) {
            // ldap:
            //   group: VALUE
            Map<Object, Object> m = (Map<Object, Object>) ldap;
            final Object value = m.get(InternalConstants.Forms.RUN_AS_GROUP_KEY);

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

        throw new ValidationErrorsException("Invalid form definition: " + formName + ". " +
                "Expected a LDAP group definition, got: " + ldap);
    }

    @SuppressWarnings("unchecked")
    private static String parseOldGroupDefinition(String formName, Object item) {
        if (item instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) item;
            Object o = m.get(InternalConstants.Forms.RUN_AS_GROUP_KEY);
            if (o instanceof String) {
                return (String) o;
            } else {
                throw invalidLdapGroupElement(formName, RUN_AS_LDAP_GROUP_PATH, o);
            }
        }

        throw invalidLdapGroupElement(formName, RUN_AS_LDAP_GROUP_PATH, item);
    }

    private static ValidationErrorsException invalidLdapGroupElement(String formName, String k, Object v) {
        return new ValidationErrorsException("Expected a string value or a group definition, " +
                "got: " + v + ". Check the '" + k + "' parameter of '" + formName + "' form definition");
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
        m2.put(FORM_FILES, tmpFiles);

        for (FormField f : fd.getFields()) {
            String k = f.getName();

            Object v = m.get(k);

            /*
             * Use cardinality as an indicator to convert single value (coming as a string) into an array
             * for the scenario when only one value was provided by the user
             */
            if (v instanceof String && (f.getCardinality() == FormField.Cardinality.ANY || f.getCardinality() == FormField.Cardinality.AT_LEAST_ONE)) {
                v = Collections.singletonList(v);
            }

            v = convert(locale, fd.getName(), f, null, v);

            boolean isOptional = f.getCardinality() == FormField.Cardinality.ONE_OR_NONE || f.getCardinality() == FormField.Cardinality.ANY;
            if (v == null && !isOptional) {
                continue;
            }

            if (f.getType().equals(ConcordFormFields.FileField.TYPE)) {
                String wsFileName = "_form_files/" + form.getFormDefinition().getName() + "/" + f.getName();
                tmpFiles.put(wsFileName, (String) v);
                m2.put(k, wsFileName);
            } else {
                m2.put(k, v);
            }
        }
        return m2;
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
                        Path tmp = IOUtils.createTempFile(f.getName(), ".tmp");
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

                    return ZonedDateTime.parse(s).withZoneSameInstant(ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);
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
                    Path tmp = IOUtils.createTempFile(f.getName(), ".tmp");
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

    public static class ValidationException extends Exception {

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
