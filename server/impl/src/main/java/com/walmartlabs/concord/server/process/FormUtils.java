package com.walmartlabs.concord.server.process;

import io.takari.bpm.form.Form;
import io.takari.bpm.form.FormValidatorLocale;
import io.takari.bpm.model.form.DefaultFormFields;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.bpm.model.form.FormField;

import javax.ws.rs.core.MultivaluedMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        Map<String, Object> m2 = new HashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            String k = e.getKey();

            FormField f = findField(fd, k);
            if (f == null) {
                continue;
            }

            Object v = convert(locale, fd.getName(), f, null, e.getValue());
            if (v == null) {
                continue;
            }

            m2.put(k, v);
        }
        return m2;
    }

    private static Object convert(FormValidatorLocale locale, String formName, FormField f, Integer idx, Object v) throws ValidationException {
        if (v instanceof String) {
            String s = (String) v;
            if (s.isEmpty()) {
                return null;
            }

            switch (f.getType()) {
                case DefaultFormFields.IntegerField.TYPE: {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        throw new ValidationException(f, s, locale.expectedInteger(formName, f.getName(), idx, s));
                    }
                }
                case DefaultFormFields.DecimalField.TYPE: {
                    try {
                        return Double.parseDouble(s);
                    } catch (NumberFormatException e) {
                        throw new ValidationException(f, s, locale.expectedDecimal(formName, f.getName(), idx, s));
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
