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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ExceptionUtils {

    public static List<Throwable> getExceptionList(Throwable e) {
        List<Throwable> list = new ArrayList<>();
        while (e != null && !list.contains(e)) {
            list.add(e);
            e = e.getCause();
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> T findLastException(T e, Class<T> clazz) {
        var exceptions = getExceptionList(e);

        for (int i = exceptions.size() - 1; i >= 0; i--) {
            var ex = exceptions.get(i);
            if (clazz.isInstance(ex)) {
                return (T) ex;
            }
        }

        return e;
    }

    private ExceptionUtils() {
    }
}
