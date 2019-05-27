package com.walmartlabs.concord.common;

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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

public final class SensitiveData {

    private static final ThreadLocal<Set<String>> sensitiveData = ThreadLocal.withInitial(Collections::emptySet);

    public interface SensitiveItem {

        Collection<String> getSensitiveData();
    }

    public static <T> T withSensitiveData(SensitiveItem item, Callable<T> c) throws Exception {
        return withSensitiveData(item.getSensitiveData(), c);
    }

    public static <T> T withSensitiveData(Collection<String> data, Callable<T> c) throws Exception {
        Set<String> current = new HashSet<>(sensitiveData.get());
        current.addAll(ensureCollection(data));

        sensitiveData.set(current);
        try {
            return c.call();
        } finally {
            sensitiveData.remove();
        }
    }

    public static String hide(String s) {
        if (s == null) {
            return null;
        }

        Set<String> data = sensitiveData.get();
        if (data.isEmpty()) {
            return s;
        }

        return hide(data, s);
    }

    public static String hide(Collection<String> data, String s) {
        for (String p : data) {
            s = s.replaceAll(p, "***");
        }
        return s;
    }

    private static Collection<String> ensureCollection(Collection<String> data) {
        if (data != null) {
            return data;
        }
        return Collections.emptyList();
    }

    private SensitiveData() {
    }
}
