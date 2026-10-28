package com.walmartlabs.concord.server.process.form;

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

import com.walmartlabs.concord.forms.ValidationError;

import javax.ws.rs.core.MultivaluedMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FormUtils {

    public static Map<String, String> mergeErrors(List<ValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }

        // TODO merge multiple errors
        Map<String, String> m = new HashMap<>();
        for (ValidationError e : errors) {
            m.put(e.fieldName(), e.error());
        }
        return m;
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
                }

                if (size == 1) {
                    m.put(k, v.get(0));
                } else {
                    m.put(k, v);
                }
            });
        }
        return m;
    }

    private FormUtils() {
    }
}
