package com.walmartlabs.concord.policyengine;

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


import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Utils {

    private static final String FILE_SIZE_UNITS = "KMGTPE";

    private static final Pattern FILE_SIZE_PATTERN = Pattern.compile("([\\d.]+)(.*)");

    public static boolean matchAny(List<String> patterns, String value) {
        for (String p : patterns) {
            if (matches(p, value)) {
                return true;
            }
        }

        return false;
    }

    public static boolean matches(String pattern, String value) {
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(value).matches();
    }

    public static Long parseFileSize(String v) {
        if (v == null) {
            return null;
        }

        v = StringUtils.upperCase(StringUtils.deleteWhitespace(v));

        Matcher m = FILE_SIZE_PATTERN.matcher(v.trim());
        if (!m.matches()) {
            throw new NumberFormatException("invalid file size format: '" + v + "'");
        }

        long number = Long.valueOf(m.group(1));
        String unit = m.group(2);

        String identifier = unit.substring(0, 1);
        int index = FILE_SIZE_UNITS.indexOf(identifier);

        if( index != -1) {
            for (int i = 0; i <= index; i++) {
                number = number * 1024;
            }
        }
        return number;
    }

    public static final class NotNullToStringStyle extends ToStringStyle {

        public static final ToStringStyle NOT_NULL_STYLE = new NotNullToStringStyle();

        private static final long serialVersionUID = 1L;

        NotNullToStringStyle() {
            super();
            this.setUseClassName(false);
            this.setUseIdentityHashCode(false);
            this.setContentStart("{");
            this.setFieldSeparator(", ");
            this.setContentEnd("}");
        }

        private Object readResolve() {
            return NOT_NULL_STYLE;
        }

        @Override
        public void append(StringBuffer buffer, String fieldName, Object value, Boolean fullDetail) {
            if (nullOrEmpty(value)) {
                return;
            }

            appendFieldStart(buffer, fieldName);
            appendInternal(buffer, fieldName, value, isFullDetail(fullDetail));
            appendFieldEnd(buffer, fieldName);
        }

        private static boolean nullOrEmpty(Object value) {
            if (value == null) {
                return true;
            }

            if (value instanceof Collection) {
                return ((Collection) value).isEmpty();
            }
            return false;
        }
    }

    private Utils() {
    }
}
