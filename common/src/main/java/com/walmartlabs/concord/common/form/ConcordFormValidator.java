package com.walmartlabs.concord.common.form;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.form.DefaultFormValidator;
import io.takari.bpm.form.FormSubmitResult;
import io.takari.bpm.form.FormValidatorLocale;
import io.takari.bpm.model.form.FormField;
import org.apache.commons.validator.routines.EmailValidator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ConcordFormValidator extends DefaultFormValidator {

    public ConcordFormValidator() {
        this(new DefaultConcordFormValidatorLocale());
    }

    public ConcordFormValidator(FormValidatorLocale locale) {
        super(createValidators(locale), locale);
    }

    private static Collection<FieldValidator> createValidators(FormValidatorLocale locale) {
        List<FieldValidator> vs = new ArrayList<>();
        vs.add(new StringFieldValidator(locale));
        vs.add(new DefaultFormValidator.IntegerFieldValidator(locale));
        vs.add(new DefaultFormValidator.DecimalFieldValidator(locale));
        vs.add(new DefaultFormValidator.BooleanFieldValidator(locale));
        vs.add(new FileFieldValidator());
        vs.add(new DateFieldValidator());
        return vs;
    }

    public static final class FileFieldValidator implements DefaultFormValidator.FieldValidator {

        private static final String[] TYPES = {ConcordFormFields.FileField.TYPE};

        @Override
        public String[] allowedTypes() {
            return TYPES;
        }

        @Override
        public FormSubmitResult.ValidationError validate(String formId, FormField f, Integer idx, Object v) {
            String fieldName = f.getName();

            if (!(v instanceof String)) {
                throw new IllegalArgumentException("Expected a file value: " + fieldName);
            }

            return null;
        }
    }

    public static final class StringFieldValidator implements DefaultFormValidator.FieldValidator {

        private final DefaultFormValidator.StringFieldValidator delegate;

        public StringFieldValidator(FormValidatorLocale locale) {
            this.delegate = new DefaultFormValidator.StringFieldValidator(locale);
        }

        @Override
        public String[] allowedTypes() {
            return delegate.allowedTypes();
        }

        @Override
        public FormSubmitResult.ValidationError validate(String formId, FormField f, Integer idx, Object v) throws ExecutionException {
            FormSubmitResult.ValidationError error = delegate.validate(formId, f, idx, v);
            if (error != null) {
                return error;
            }
            String inputType = f.getOption(new FormField.Option<>("inputType", String.class));
            if ("email".equalsIgnoreCase(inputType)) {
                boolean valid = EmailValidator.getInstance().isValid((String)v);
                if (!valid) {
                    return new FormSubmitResult.ValidationError(f.getName(), "Invalid email address");
                }
            }

            return null;
        }
    }

    public static final class DateFieldValidator implements DefaultFormValidator.FieldValidator {

        private static final String[] TYPES = {ConcordFormFields.DateField.TYPE, ConcordFormFields.DateTimeField.TYPE};

        @Override
        public String[] allowedTypes() {
            return TYPES;
        }

        @Override
        public FormSubmitResult.ValidationError validate(String formId, FormField f, Integer idx, Object v) {
            String fieldName = f.getName();

            if (!(v instanceof String)) {
                throw new IllegalArgumentException("Expected a date value: " + fieldName);
            }

            return null;
        }
    }
}
