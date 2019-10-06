package com.walmartlabs.concord.db;

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

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;

import java.sql.Timestamp;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;

public final class PgUtils {

    public static Condition contains(Field<String[]> left, String[] right) {
        return DSL.condition("{0} @> {1}::text[]", left, DSL.val(right, left.getDataType()));
    }

    public static Field<?> interval(String s) {
        return field("interval '" + s + "'");
    }

    public static Field<String> toChar(Field<Timestamp> date, String format) {
        return field("to_char({0}, {1})", String.class, date, inline(format));
    }

    public static Field<String> jsonbText(Field<JSONB> field, String name) {
        return field("{0}::jsonb->>{1}", Object.class, field, inline(name)).cast(String.class);
    }

    public static Condition jsonbEq(Field<JSONB> field, String key, String value) {
        return DSL.condition("{0} @> jsonb_build_object({1}, {2})", field, DSL.val(key), DSL.value(value));
    }

    private PgUtils() {
    }
}
