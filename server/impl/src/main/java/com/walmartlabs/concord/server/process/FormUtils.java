package com.walmartlabs.concord.server.process;

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
import com.walmartlabs.concord.project.ConcordFormFields;
import io.takari.bpm.form.Form;
import io.takari.bpm.form.FormValidatorLocale;
import io.takari.bpm.model.form.DefaultFormFields;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.bpm.model.form.FormField;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.project.InternalConstants.Files.FORM_FILES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public final class FormUtils {

    private static FormField findField(FormDefinition fd, String fieldName) {
        for (FormField f : fd.getFields()) {
            if (fieldName.equals(f.getName())) {
                return f;
            }
        }
        return null;
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
                } else if (size == 1) {
                    m.put(k, v.get(0));
                } else {
                    m.put(k, v);
                }
            });
        }
        return m;
    }

    // TODO this probably should be a part of the bpm engine's FormService
    public static Map<String, Object> convert(FormValidatorLocale locale, Form form, Map<String, Object> m) throws ValidationException {
        FormDefinition fd = form.getFormDefinition();

        Map<String, String> tmpFiles = new HashMap<>();
        Map<String, Object> m2 = new HashMap<>();
        m2.put(FORM_FILES, tmpFiles);

        for (FormField f : fd.getFields()) {
            String k = f.getName();

            Object v = m.get(k);
            v = convert(locale, fd.getName(), f, null, v);

            boolean isOptional = f.getCardinality() == FormField.Cardinality.ONE_OR_NONE || f.getCardinality() == FormField.Cardinality.ANY;
            if (v == null && !isOptional) {
                continue;
            }

            if (f.getType().equals(ConcordFormFields.FileField.TYPE)) {
                String wsFileName = "_form_files/" + form.getFormDefinition().getName() + "/" + f.getName();
                tmpFiles.put(wsFileName, (String)v);
                m2.put(k, wsFileName);
            } else {
                m2.put(k, v);
            }
        }
        return m2;
    }

    private static Object convert(FormValidatorLocale locale, String formName, FormField f, Integer idx, Object v) throws ValidationException {
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
                        throw new WebApplicationException("Error reading file for form field '" + f.getName() + "'", e);
                    }
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
                try (InputStream is = (InputStream) v){
                    Path tmp = IOUtils.createTempFile(f.getName(), ".tmp");
                    Files.copy(is, tmp, REPLACE_EXISTING);
                    return tmp.toString();
                } catch (IOException e) {
                    throw new WebApplicationException("Error reading file for form field '" + f.getName() + "'", e);
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
