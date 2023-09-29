package com.walmartlabs.concord.client2;

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

import com.walmartlabs.concord.client2.ImmutableProcessListFilter;
import com.walmartlabs.concord.client2.ProcessDataInclude;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface ProcessListFilter {

    @Nullable
    UUID orgId();

    @Nullable
    String orgName();

    @Nullable
    UUID projectId();

    @Nullable
    String projectName();

    @Nullable
    UUID repoId();

    @Nullable
    String repoName();

    @Nullable
    OffsetDateTimeParam afterCreatedAt();

    @Nullable
    OffsetDateTimeParam beforeCreatedAt();

    @Nullable
    Set<String> tags();

    @Nullable
    String status();

    @Nullable
    String initiator();

    @Nullable
    UUID parentInstanceId();

    @Nullable
    Set<String> include();

    @Nullable
    Integer limit();

    @Nullable
    Integer offset();

    @Nullable
    Map<String, String> meta();

    class Builder extends ImmutableProcessListFilter.Builder {

        public Builder status(ProcessEntry.StatusEnum status) {
            return status(status.getValue());
        }

        public Builder addInclude(ProcessDataInclude... elements) {
            for (ProcessDataInclude e : elements) {
                addInclude(e.getValue());
            }
            return this;
        }

        public Builder afterCreatedAt(OffsetDateTime afterCreatedAt) {
            if (afterCreatedAt == null) {
                return this;
            }

            afterCreatedAt(new OffsetDateTimeParam().value(afterCreatedAt));
            return this;
        }
    }

    static ImmutableProcessListFilter.Builder builder() {
        return new Builder();
    }
}
