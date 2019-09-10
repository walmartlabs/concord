package com.walmartlabs.concord.server.process.queue;

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

import com.walmartlabs.concord.server.process.queue.ProcessFilter.FilterType;
import com.walmartlabs.concord.server.process.queue.ProcessFilter.MetadataFilter;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectQuery;

import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.db.PgUtils.jsonText;

public final class MetadataUtils {

    private static final SuffixMapping[] SUFFIX_MAPPINGS = {
            new SuffixMapping(".contains", FilterType.CONTAINS),
            new SuffixMapping(".notContains", FilterType.NOT_CONTAINS),

            new SuffixMapping(".eq", FilterType.EQUALS),
            new SuffixMapping(".notEq", FilterType.NOT_EQUALS),

            new SuffixMapping(".startsWith", FilterType.STARTS_WITH),
            new SuffixMapping(".notStartsWith", FilterType.NOT_STARTS_WITH),

            new SuffixMapping(".endsWith", FilterType.ENDS_WITH),
            new SuffixMapping(".notEndsWith", FilterType.NOT_ENDS_WITH)
    };

    public static List<MetadataFilter> parseMetadataFilters(UriInfo uriInfo) {
        return uriInfo.getQueryParameters().entrySet().stream()
                .filter(e -> e.getKey().startsWith("meta."))
                .map(e -> parseMetadataFilter(e.getKey().substring("meta.".length()), e.getValue().get(0)))
                .collect(Collectors.toList());
    }

    public static void apply(SelectQuery<Record> q, Field<?> column, List<MetadataFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }

        for (MetadataFilter f : filters) {
            switch (f.type()) {
                case CONTAINS: {
                    q.addConditions(jsonText(column, f.key()).contains(f.value()));
                    break;
                }
                case NOT_CONTAINS: {
                    q.addConditions(jsonText(column, f.key()).notContains(f.value()));
                    break;
                }
                case EQUALS: {
                    q.addConditions(jsonText(column, f.key()).eq(f.value()));
                    break;
                }
                case NOT_EQUALS: {
                    q.addConditions(jsonText(column, f.key()).notEqual(f.value()));
                    break;
                }
                case STARTS_WITH: {
                    q.addConditions(jsonText(column, f.key()).startsWith(f.value()));
                    break;
                }
                case NOT_STARTS_WITH: {
                    q.addConditions(jsonText(column, f.key()).startsWith(f.value()).not());
                    break;
                }
                case ENDS_WITH: {
                    q.addConditions(jsonText(column, f.key()).endsWith(f.value()));
                    break;
                }
                case NOT_ENDS_WITH: {
                    q.addConditions(jsonText(column, f.key()).endsWith(f.value()).not());
                    break;
                }
            }
        }
    }

    private static MetadataFilter parseMetadataFilter(String key, String value) {
        ImmutableMetadataFilter.Builder b = MetadataFilter.builder()
                .value(value);

        if (!key.contains(".")) {
            return b.key(key).build();
        }

        for (SuffixMapping m : SUFFIX_MAPPINGS) {
            if (key.endsWith(m.suffix)) {
                String k = key.substring(0, key.length() - m.suffix.length());
                return b.key(k).type(m.filterType).build();
            }
        }

        throw new IllegalArgumentException("Invalid metadata key: " + key);
    }

    private static final class SuffixMapping {

        private final String suffix;
        private final FilterType filterType;

        private SuffixMapping(String suffix, FilterType filterType) {
            this.suffix = suffix;
            this.filterType = filterType;
        }
    }
}
