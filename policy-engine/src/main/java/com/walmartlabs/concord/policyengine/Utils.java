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

import com.walmartlabs.concord.common.Matcher;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class Utils {

    private static final String FILE_SIZE_UNITS = "KMGTPE";

    private static final Pattern FILE_SIZE_PATTERN = Pattern.compile("([\\d.]+)(.*)");

    public static boolean matchAny(List<String> patterns, String value) {
        return Matcher.matchAny(patterns, value);
    }

    public static boolean matches(String pattern, String value) {
        return Matcher.matches(value, pattern);
    }

    public static boolean matches(Map<String, Object> conditions, Map<String, Object> data) {
        return Matcher.matches(data, conditions);
    }

    public static Long parseFileSize(String v) {
        if (v == null) {
            return null;
        }

        v = deleteAllWhitespace(v);

        java.util.regex.Matcher m = FILE_SIZE_PATTERN.matcher(v.toUpperCase());
        if (!m.matches()) {
            throw new NumberFormatException("invalid file size format: '" + v + "'");
        }

        long number = Long.parseLong(m.group(1));
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

    private static String deleteAllWhitespace(String str) {
        if (str == null || str.trim().isEmpty()) {
            return str;
        }

        char[] ch = new char[str.length()];
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                ch[count++] = str.charAt(i);
            }
        }
        if (count == str.length()) {
            return str;
        }
        return new String(ch, 0, count);
    }

    private Utils() {
    }
}
