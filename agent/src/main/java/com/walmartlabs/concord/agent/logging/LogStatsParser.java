package com.walmartlabs.concord.agent.logging;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class LogStatsParser {

    static final byte[] START_STATS = "\0<<<CONCORD_STATS:".getBytes();
    static final byte[] END_STATS = ">>>\n".getBytes();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static Result parse(byte[] ab, int len) throws IOException {
        List<Bytes.Range> stats = Bytes.partialIndexOfAll(ab, len, START_STATS, START_STATS.length, END_STATS, END_STATS.length);
        byte[] chunkBuffer = Bytes.remove(ab, len, normalize(stats, len, END_STATS.length));

        if (stats.isEmpty()) {
            return Result.of(chunkBuffer, null, len);
        }

        Bytes.Range statsRange = stats.get(stats.size() - 1);
        boolean partialEnd = statsRange.end() == null || statsRange.end() + END_STATS.length > len;
        if (partialEnd) {
            return Result.of(chunkBuffer, null, statsRange.start());
        }

        int end = Objects.requireNonNull(statsRange.end());
        int offset = statsRange.start() + START_STATS.length;
        int statsLen = end - statsRange.start() - START_STATS.length;
        try {
            return Result.of(chunkBuffer, OBJECT_MAPPER.readValue(ab, offset, statsLen, LogSegmentStats.class), len);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static List<Bytes.Range> normalize(List<Bytes.Range> ranges, int abLen, int endPatternLen) {
        return ranges.stream()
                .map(r -> Bytes.Range.of(r.start(), r.end() == null ? abLen - 1: Math.min(abLen, Objects.requireNonNull(r.end()) + endPatternLen) - 1))
                .collect(Collectors.toList());
    }

    @Value.Immutable
    interface Result {

        @Nullable
        @Value.Parameter
        byte[] chunk();

        @Nullable
        @Value.Parameter
        LogSegmentStats stats();

        @Value.Parameter
        int readPos();

        static Result of(byte[] chunk, LogSegmentStats stats, int readPos) {
            return ImmutableResult.of(chunk, stats, readPos);
        }
    }
}
