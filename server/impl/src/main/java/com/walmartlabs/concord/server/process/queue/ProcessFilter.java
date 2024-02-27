package com.walmartlabs.concord.server.process.queue;

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

import com.walmartlabs.concord.server.process.ProcessDataInclude;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Value.Immutable
public interface ProcessFilter {

    @Nullable
    OffsetDateTime afterCreatedAt();

    @Nullable
    OffsetDateTime beforeCreatedAt();

    @Nullable
    String initiator();

    @Nullable
    Set<UUID> orgIds();

    @Nullable
    UUID parentId();

    @Nullable
    UUID projectId();

    @Nullable
    UUID repoId();

    @Nullable
    String repoName();

    /**
     * Include processes without projects.
     * Applied only when {@link #orgIds()} and/or {@link #projectId()} are defined.
     */
    @Value.Default
    default boolean includeWithoutProject() {
        return true;
    }

    @Nullable
    ProcessStatus status();

    @Nullable
    Set<String> tags();

    @Value.Default
    default Set<ProcessDataInclude> includes() {
        return Collections.emptySet();
    }

    @Nullable
    List<MetadataFilter> metaFilters();

    @Nullable
    List<JsonFilter> requirements();

    @Nullable
    List<DateFilter> startAt();

    @Nullable
    Integer limit();

    @Nullable
    Integer offset();

    static ImmutableProcessFilter.Builder builder() {
        return ImmutableProcessFilter.builder();
    }

    @Value.Immutable
    interface JsonFilter {

        @Value.Default
        default FilterType type() {
            return FilterType.CONTAINS;
        }

        List<String> path();

        @Nullable
        String value();

        static ImmutableJsonFilter.Builder builder() {
            return ImmutableJsonFilter.builder();
        }
    }

    @Value.Immutable
    interface DateFilter {

        @Value.Default
        default FilterType type() {
            return FilterType.EQUALS;
        }

        @Nullable
        OffsetDateTime value();
    }

    @Value.Immutable
    interface MetadataFilter {

        @Value.Default
        default FilterType type() {
            return FilterType.CONTAINS;
        }

        String key();

        String value();

        static ImmutableMetadataFilter.Builder builder() {
            return ImmutableMetadataFilter.builder();
        }
    }

    enum FilterType {
        CONTAINS,
        NOT_CONTAINS,

        EQUALS,
        NOT_EQUALS,

        ENDS_WITH,
        NOT_ENDS_WITH,

        STARTS_WITH,
        NOT_STARTS_WITH,

        GREATER_OR_EQUALS,
        LESS_OR_EQUALS_OR_NULL,

        REGEXP_MATCH
    }
}
