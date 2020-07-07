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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    public static boolean matches(Map<String, Object> conditions, Map<String, Object> data) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        return compareNodes(data, conditions);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean compareNodes(Object data, Object conditions) {
        if (data == null && conditions == null) {
            return true;
        } else if (data == null || conditions == null) {
            return false;
        }

        if (conditions instanceof Map && data instanceof Map) {
            return compareObjectNodes((Map<String, Object>) data, (Map<String, Object>) conditions);
        } else if (conditions instanceof String && data instanceof UUID) {
            return matches((String)conditions, data.toString());
        } else if (conditions instanceof String && data instanceof String) {
            return matches((String)conditions, (String)data);
        } else if (conditions instanceof Collection && data instanceof Collection) {
            return compareArrayNodes((Collection) data, (Collection) conditions);
        } else if (conditions instanceof Collection) {
            return matchAny(data, (Collection)conditions);
        } else {
            return data.equals(conditions);
        }
    }

    private static boolean compareObjectNodes(Map<String, Object> data, Map<String, Object> conditions) {
        for (Map.Entry<String, Object> e : conditions.entrySet()) {
            Object dataItem = data.get(e.getKey());
            if (!compareNodes(dataItem, e.getValue())) {
                return false;
            }
        }

        return true;
    }

    private static boolean compareArrayNodes(Collection<Object> dataElements, Collection<Object> conditionElements) {
        if (conditionElements.size() > dataElements.size()) {
            return false;
        }

        for (Object c : conditionElements) {
            boolean matched = matchAny(c, dataElements);
            if (!matched) {
                return false;
            }
        }

        return true;
    }

    private static boolean matchAny(Object condition, Collection<Object> nodes) {
        for (Object n : nodes) {
            boolean result = compareNodes(n, condition);
            if (result) {
                return true;
            }
        }

        return false;
    }

    public static Long parseFileSize(String v) {
        if (v == null) {
            return null;
        }

        v = deleteAllWhitespace(v);

        Matcher m = FILE_SIZE_PATTERN.matcher(v.toUpperCase());
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
