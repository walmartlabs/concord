package com.walmartlabs.concord.client2.impl;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import java.util.Collections;
import java.util.List;

public class Headers {

    private final List<NameValuePair> items;

    public static Headers of(String name, String value) {
        return new Headers(Collections.singletonList(new NameValuePair(name, value)));
    }

    public Headers(List<NameValuePair> items) {
        this.items = items;
    }

    public String get(String name) {
        return items.stream()
                .filter(nvp -> nvp.getName().equalsIgnoreCase(name))
                .map(NameValuePair::getValue)
                .findFirst()
                .orElse(null);
    }

    public int size() {
        return items.size();
    }

    public String name(int index) {
        return items.get(index).getName();
    }

    public String value(int index) {
        return items.get(index).getValue();
    }
}
