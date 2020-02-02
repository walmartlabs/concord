package com.walmartlabs.concord.server.plugins.noderoster.processor;

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

import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;

/**
 * Handles partitioned tables. Assumes {@code table_yyyyMMdd} format
 * of partition names.
 */
public class Partitioner<E, R extends Record> {

    // TODO make configurable
    private static final String PARTITION_DATE_FORMAT = "yyyyMMdd";

    private final Table<R> table;
    private final Function<E, Timestamp> keyGetter;

    private final boolean enabled;

    public Partitioner(Table<R> table, Function<E, Timestamp> keyGetter) {
        this.table = table;
        this.keyGetter = keyGetter;
        this.enabled = "true".equals(System.getenv("NODE_ROSTER_PARTITIONING_ENABLED"));
    }

    public Map<Table<R>, Collection<E>> process(Collection<E> items) {
        if (!enabled) {
            return Collections.singletonMap(table, items);
        }

        Map<Table<R>, Collection<E>> result = new HashMap<>();
        for (E i : items) {
            Timestamp itemKey = keyGetter.apply(i);
            String partitionId = partitionId(itemKey);
            Table<R> t = table(table, partitionId);

            result.computeIfAbsent(t, recordTable -> new ArrayList<>())
                    .add(i);
        }

        return result;
    }

    private static String partitionId(Timestamp itemKey) {
        Timestamp start = startOfDay(itemKey);
        return new SimpleDateFormat(PARTITION_DATE_FORMAT).format(start);
    }

    private static Timestamp startOfDay(Timestamp ts) {
        return Timestamp.valueOf(ts.toLocalDateTime().toLocalDate().atTime(LocalTime.MIN));
    }

    @SuppressWarnings("unchecked")
    private static <E extends Record> Table<E> table(Table<?> table, String partitionId) {
        return (Table<E>) DSL.table(DSL.name(table.getName() + "_" + partitionId));
    }
}
