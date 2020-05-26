package com.walmartlabs.concord.server.process.queue;

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

import com.walmartlabs.concord.server.jooq.tables.records.ProcessQueueRecord;
import org.immutables.value.Value;
import org.jooq.Record;
import org.jooq.SelectQuery;
import org.jooq.TableField;

import javax.ws.rs.core.UriInfo;
import javax.xml.bind.DatatypeConverter;
import java.sql.Timestamp;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public final class FilterUtils {

    public static final SuffixMapping[] SUFFIX_MAPPINGS = {
            SuffixMapping.of(".contains", ProcessFilter.FilterType.CONTAINS),
            SuffixMapping.of(".notContains", ProcessFilter.FilterType.NOT_CONTAINS),

            SuffixMapping.of(".eq", ProcessFilter.FilterType.EQUALS),
            SuffixMapping.of(".notEq", ProcessFilter.FilterType.NOT_EQUALS),

            SuffixMapping.of(".startsWith", ProcessFilter.FilterType.STARTS_WITH),
            SuffixMapping.of(".notStartsWith", ProcessFilter.FilterType.NOT_STARTS_WITH),

            SuffixMapping.of(".endsWith", ProcessFilter.FilterType.ENDS_WITH),
            SuffixMapping.of(".notEndsWith", ProcessFilter.FilterType.NOT_ENDS_WITH)
    };

    public static ProcessFilter.DateFilter parseDate(String paramName, UriInfo uriInfo) {
        for (Map.Entry<String, List<String>> e : uriInfo.getQueryParameters().entrySet()) {
            String k = e.getKey();
            if (k.startsWith(paramName + ".") || k.equals(paramName)) {
                String value = null;
                if (e.getValue() != null && !e.getValue().isEmpty()) {
                    value = e.getValue().get(0);
                }
                return parseDateFilter(k, value);
            }
        }

        return null;
    }

    public static void apply(SelectQuery<Record> q, TableField<ProcessQueueRecord, Timestamp> field, ProcessFilter.DateFilter filter) {
        if (filter == null) {
            return;
        }

        switch (filter.type()) {
            case EQUALS: {
                if (filter.value() == null) {
                    q.addConditions(field.isNull());
                } else {
                    q.addConditions(field.eq(filter.value()));
                }
                break;
            }
            case NOT_EQUALS: {
                if (filter.value() == null) {
                    q.addConditions(field.isNotNull());
                } else {
                    q.addConditions(field.notEqual(filter.value()));
                }
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported filter type: " + filter.type());
        }
    }

    @Value.Immutable
    public interface SuffixMapping {

        @Value.Parameter
        String suffix();

        @Value.Parameter
        ProcessFilter.FilterType filterType();

        static SuffixMapping of(String suffix, ProcessFilter.FilterType filterType) {
            return ImmutableSuffixMapping.of(suffix, filterType);
        }
    }

    private static ProcessFilter.DateFilter parseDateFilter(String key, String value) {
        ImmutableDateFilter.Builder b = ImmutableDateFilter.builder()
                .value(parseDateValue(value));

        if (!key.contains(".")) {
            return b.build();
        }

        for (FilterUtils.SuffixMapping m : FilterUtils.SUFFIX_MAPPINGS) {
            if (key.endsWith(m.suffix())) {
                return b.type(m.filterType()).build();
            }
        }

        throw new IllegalArgumentException("Invalid data filter key: " + key);
    }

    private static Timestamp parseDateValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            Calendar c = DatatypeConverter.parseDateTime(value);
            return new Timestamp(c.getTimeInMillis());
        } catch (DateTimeParseException e) {
            throw new RuntimeException("Invalid date format, expected an ISO-8601 value, got: " + value);
        }
    }
}
