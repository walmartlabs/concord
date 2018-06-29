package com.walmartlabs.concord.project.yaml;

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

import io.takari.parc.Input;

import java.util.List;

public class ListInput<T> implements Input<T> {

    private final int pos;
    private final List<T> items;

    public ListInput(List<T> items) {
        this(0, items);
    }

    private ListInput(int pos, List<T> items) {
        this.pos = pos;
        this.items = items;
    }

    @Override
    public int position() {
        return pos;
    }

    @Override
    public T first() {
        return items.get(pos);
    }

    @Override
    public Input<T> rest() {
        return new ListInput<>(pos + 1, items);
    }

    @Override
    public boolean end() {
        return pos >= items.size();
    }
}
