package com.walmartlabs.concord.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

public final class NashornUtils {

    /**
     * Returns <code>true</code> if the provided object is
     * JavaScript array type provided by Nashorn or a <code>null</code> value.
     */
    public static boolean isNashornArray(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!isNashornScriptObjectMirror(obj)) {
            return false;
        }

        Method isArray = getMethod(obj, "isArray");

        try {
            return (boolean) isArray.invoke(obj);
        } catch (IllegalAccessException | InvocationTargetException e) {
            // this ScriptObjectMirror looks weird
            // unexpected implementation in the classpath?
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns <code>true</code> if the provided object is
     * a <code>jdk.nashorn.api.scripting.ScriptObjectMirror</code> instance.
     * Doesn't accept <code>null</code> values.
     */
    public static boolean isNashornScriptObjectMirror(Object obj) {
        Class<?> klass = obj.getClass();
        return klass.getName().equals("jdk.nashorn.api.scripting.ScriptObjectMirror");
    }

    /**
     * Calls <code>jdk.nashorn.api.scripting.ScriptObjectMirror#entrySet()</code> on
     * the provided object. Throws runtime exceptions if the provided object is not
     * a JavaScript Object type provided by Nashorn.
     */
    @SuppressWarnings("unchecked")
    public static Set<Map.Entry<String, Object>> getNashornObjectEntrySet(Object obj) {
        Method entrySet = getMethod(obj, "entrySet");
        try {
            return (Set<Map.Entry<String, Object>>) entrySet.invoke(obj);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static Method getMethod(Object obj, String methodName) {
        try {
            return obj.getClass().getDeclaredMethod(methodName);
        } catch (NoSuchMethodException e) {
            // this ScriptObjectMirror looks weird
            throw new RuntimeException(e);
        }
    }
}
