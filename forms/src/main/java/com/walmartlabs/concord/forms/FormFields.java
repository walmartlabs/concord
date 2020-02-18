package com.walmartlabs.concord.forms;

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

import static com.walmartlabs.concord.forms.FormField.Option;

/**
 * Standard form field types and options.
 */
public final class FormFields {

    public static final class StringField {

        public static final String TYPE = "string";
        public static final Option<String> PATTERN = Option.of("pattern", String.class);
        public static final Option<String> INPUT_TYPE = Option.of("inputType", String.class);
        public static final Option<String> PLACEHOLDER = Option.of("placeholder", String.class);
        public static final Option<Boolean> SEARCH = Option.of("search", Boolean.class);
    }

    public static final class IntegerField {

        public static final String TYPE = "int";
        public static final Option<Long> MIN = Option.of("min", Long.class);
        public static final Option<Long> MAX = Option.of("max", Long.class);
        public static final Option<String> PLACEHOLDER = Option.of("placeholder", String.class);
    }

    public static final class DecimalField {

        public static final String TYPE = "decimal";
        public static final Option<Double> MIN = Option.of("min", Double.class);
        public static final Option<Double> MAX = Option.of("max", Double.class);
        public static final Option<String> PLACEHOLDER = Option.of("placeholder", String.class);
    }

    public static final class BooleanField {

        public static final String TYPE = "boolean";
    }

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

        public static final Option<String> POPUP_POSITION = Option.of("popupPosition", String.class);

        private DateFieldOptions() {
        }
    }

    public static final class CommonFieldOptions {

        public static final Option<Boolean> READ_ONLY = Option.of("readOnly", Boolean.class);

        private CommonFieldOptions() {
        }
    }

    private FormFields() {
    }
}
