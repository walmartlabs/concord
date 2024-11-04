package com.walmartlabs.concord.plugins.misc;

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

import com.walmartlabs.concord.runtime.v2.sdk.DryRunReady;
import com.walmartlabs.concord.runtime.v2.sdk.Task;

import javax.inject.Named;

@Named("misc")
@SuppressWarnings("unused")
@DryRunReady
public class MiscTaskV2 implements Task {

    public void throwRuntimeException(String message) {
        throw new RuntimeException(message);
    }

    public String trim(String s, int length) {
        if (s == null) {
            return s;
        }

        if (s.length() <= length) {
            return s;
        }

        int newLength = length - 3;
        return s.substring(0, newLength) + "...";
    }
}
