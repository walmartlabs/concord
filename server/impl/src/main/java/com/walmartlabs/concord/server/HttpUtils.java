package com.walmartlabs.concord.server;

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

import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import javax.ws.rs.core.Response;

public final class HttpUtils {

    @Value.Immutable
    public interface Range {

        @Nullable
        Integer start();

        @Nullable
        Integer end();

        static ImmutableRange.Builder builder() {
            return ImmutableRange.builder();
        }
    }

    public static Range parseRangeHeaderValue(String range) {
        if (range == null || range.trim().isEmpty()) {
            return Range.builder().build();
        }

        if (!range.startsWith("bytes=")) {
            throw new ConcordApplicationException("Invalid range header: " + range, Response.Status.BAD_REQUEST);
        }

        ImmutableRange.Builder builder = Range.builder();
        String[] as = range.substring("bytes=".length()).split("-");
        if (as.length > 0) {
            try {
                builder.start(Integer.parseInt(as[0]));
            } catch (NumberFormatException ignored) {
            }
        }

        if (as.length > 1) {
            try {
                builder.end(Integer.parseInt(as[1]));
            } catch (NumberFormatException ignored) {
            }
        }
        return builder.build();
    }

    private HttpUtils() {
    }
}
