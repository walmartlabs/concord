package com.walmartlabs.concord.common.form;

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


import static io.takari.bpm.model.form.FormField.Option;

public final class ConcordFormFields {

    public static final class FileField {

        public static final String TYPE = "file";

        private FileField() {
        }
    }

    public static final class DateField {

        public static final String TYPE = "date";

        private DateField() {
        }
    }

    public static final class DateTimeField {

        public static final String TYPE = "dateTime";

        private DateTimeField() {
        }
    }

    public static final class DateFieldOptions {
        public static final Option<String> POPUP_POSITION = new Option<>("popupPosition", String.class);

        private DateFieldOptions() {
        }
    }
    public static final class FieldOptions {

        public static final Option<String> INPUT_TYPE = new Option<>("inputType", String.class);
        public static final Option<String> PLACEHOLDER = new Option<>("placeholder", String.class);
        public static final Option<Boolean> READ_ONLY = new Option<>("readOnly", Boolean.class);
        public static final Option<Boolean> SEARCH = new Option<>("search", Boolean.class);

        private FieldOptions() {
        }
    }

    private ConcordFormFields() {
    }
}
