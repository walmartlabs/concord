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

import com.walmartlabs.concord.common.DateTimeUtils;
import org.immutables.value.Value;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.SelectQuery;

import javax.ws.rs.core.UriInfo;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.db.PgUtils.*;
import static org.jooq.impl.DSL.currentOffsetDateTime;

public final class FilterUtils {

    public static final SuffixMapping[] SUFFIX_MAPPINGS = {
            SuffixMapping.of(".contains", ProcessFilter.FilterType.CONTAINS),
            SuffixMapping.of(".notContains", ProcessFilter.FilterType.NOT_CONTAINS),

            SuffixMapping.of(".eq", ProcessFilter.FilterType.EQUALS),
            SuffixMapping.of(".notEq", ProcessFilter.FilterType.NOT_EQUALS),

            SuffixMapping.of(".startsWith", ProcessFilter.FilterType.STARTS_WITH),
            SuffixMapping.of(".notStartsWith", ProcessFilter.FilterType.NOT_STARTS_WITH),

            SuffixMapping.of(".endsWith", ProcessFilter.FilterType.ENDS_WITH),
            SuffixMapping.of(".notEndsWith", ProcessFilter.FilterType.NOT_ENDS_WITH),

            SuffixMapping.of(".ge", ProcessFilter.FilterType.GREATER_OR_EQUALS),
            SuffixMapping.of(".len", ProcessFilter.FilterType.LESS_OR_EQUALS_OR_NULL),

            SuffixMapping.of(".regexp", ProcessFilter.FilterType.REGEXP_MATCH)
    };

    public static List<ProcessFilter.DateFilter> parseDate(String paramName, UriInfo uriInfo) {
        return uriInfo.getQueryParameters().entrySet().stream()
                .filter(e -> isParam(e.getKey(), paramName))
                .map(e -> {
                    ImmutableDateFilter.Builder b = ImmutableDateFilter.builder()
                            .value(parseDateValue(getValue(e)));

                    ProcessFilter.FilterType type = parseFilterType(e.getKey());
                    if (type != null) {
                        b.type(type);
                    }
                    return b.build();
                })
                .collect(Collectors.toList());
    }

    public static List<ProcessFilter.JsonFilter> parseJson(String paramName, UriInfo uriInfo) {
        return uriInfo.getQueryParameters().entrySet().stream()
                .filter(e -> (isParam(e.getKey(), paramName)))
                .map(e -> {
                    ImmutableJsonFilter.Builder filter = ProcessFilter.JsonFilter.builder()
                            .value(getValue(e));

                    // skip paramName
                    List<String> path = Arrays.stream(e.getKey().split("\\."))
                            .skip(1)
                            .collect(Collectors.toList());

                    ProcessFilter.FilterType type = parseFilterType(e.getKey());
                    if (type != null) {
                        filter.type(type);

                        path = new ArrayList<>(path);
                        path.remove(path.size() - 1);
                    }

                    return filter.path(path)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public static void applyDate(SelectQuery<Record> q, Field<OffsetDateTime> field, List<ProcessFilter.DateFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }

        for (ProcessFilter.DateFilter filter : filters) {
            switch (filter.type()) {
                case GREATER_OR_EQUALS: {
                    if (filter.value() == null) {
                        q.addConditions(field.greaterOrEqual(currentOffsetDateTime()));
                    } else {
                        q.addConditions(field.greaterOrEqual(filter.value()));
                    }
                    break;
                }
                case LESS_OR_EQUALS_OR_NULL: {
                    if (filter.value() == null) {
                        q.addConditions(field.lessOrEqual(currentOffsetDateTime()).or(field.isNull()));
                    } else {
                        q.addConditions(field.lessOrEqual(filter.value()).or(field.isNull()));
                    }
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unsupported filter type: " + filter.type());
            }
        }
    }

    public static void applyJson(SelectQuery<Record> q, Field<JSONB> column, List<ProcessFilter.JsonFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }

        for (ProcessFilter.JsonFilter f : filters) {
            switch (f.type()) {
                case CONTAINS: {
                    q.addConditions(jsonbTextByPath(column, f.path()).contains(f.value()));
                    break;
                }
                case NOT_CONTAINS: {
                    q.addConditions(jsonbTextByPath(column, f.path()).notContains(f.value()));
                    break;
                }
                case EQUALS: {
                    q.addConditions(jsonbTextExistsByPath(column, f.path(), f.value()));
                    break;
                }
                case NOT_EQUALS: {
                    q.addConditions(jsonbTextNotExistsByPath(column, f.path(), f.value()));
                    break;
                }
                case REGEXP_MATCH: {
                    q.addConditions(jsonbTextMatch(column, f.path(), f.value()));
                    break;
                }
                case STARTS_WITH: {
                    q.addConditions(jsonbTextByPath(column, f.path()).startsWith(f.value()));
                    break;
                }
                case NOT_STARTS_WITH: {
                    q.addConditions(jsonbTextByPath(column, f.path()).startsWith(f.value()).not());
                    break;
                }
                case ENDS_WITH: {
                    q.addConditions(jsonbTextByPath(column, f.path()).endsWith(f.value()));
                    break;
                }
                case NOT_ENDS_WITH: {
                    q.addConditions(jsonbTextByPath(column, f.path()).endsWith(f.value()).not());
                    break;
                }
            }
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

    private static ProcessFilter.FilterType parseFilterType(String key) {
        if (!key.contains(".")) {
            return null;
        }

        for (SuffixMapping m : FilterUtils.SUFFIX_MAPPINGS) {
            if (key.endsWith(m.suffix())) {
                return m.filterType();
            }
        }

        return null;
    }

    private static OffsetDateTime parseDateValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return DateTimeUtils.fromIsoString(value);
        } catch (DateTimeParseException e) {
            throw new RuntimeException("Invalid date format, expected an ISO-8601 value, got: " + value);
        }
    }

    private static boolean isParam(String key, String paramName) {
        return key.startsWith(paramName + ".") || key.equals(paramName);
    }

    private static String getValue(Map.Entry<String, List<String>> e) {
        if (e.getValue() != null && !e.getValue().isEmpty()) {
            return e.getValue().get(0);
        }
        return null;
    }
}
