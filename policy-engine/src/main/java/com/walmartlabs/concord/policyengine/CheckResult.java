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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CheckResult<R, E> {

    private static final CheckResult SUCCESS = new CheckResult<>();

    @SuppressWarnings("unchecked")
    public static <R, E> CheckResult<R, E> success() {
        return (CheckResult<R, E>)SUCCESS;
    }

    private final List<Item<R, E>> warn;
    private final List<Item<R, E>> deny;

    public CheckResult() {
        this(Collections.emptyList(), Collections.emptyList());
    }

    public CheckResult(List<Item<R, E>> warn, List<Item<R, E>> deny) {
        this.warn = warn;
        this.deny = deny;
    }

    @SafeVarargs
    public static <R, E> CheckResult<R, E> warn(Item<R, E> ... items) {
        return new CheckResult<R, E>(Arrays.asList(items), Collections.emptyList());
    }

    @SafeVarargs
    public static <R, E> CheckResult<R, E> error(Item<R, E> ... items) {
        return new CheckResult<R, E>(Collections.emptyList(), Arrays.asList(items));
    }

    public List<Item<R, E>> getWarn() {
        return warn;
    }

    public List<Item<R, E>> getDeny() {
        return deny;
    }

    public static class Item<R, E> {

        private final R rule;
        private final E entity;
        private final String msg;

        public Item(R rule, E entity) {
            this(rule, entity, null);
        }

        public Item(R rule, E entity, String msg) {
            this.rule = rule;
            this.entity = entity;
            this.msg = msg;
        }

        public R getRule() {
            return rule;
        }

        public E getEntity() {
            return entity;
        }

        public String getMsg() {
            return msg;
        }

        @Override
        public String toString() {
            return msg;
        }
    }
}
