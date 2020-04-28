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

import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Bytes {

    @Value.Immutable
    public interface Range {

        @Value.Parameter
        int start();

        @Nullable
        @Value.Parameter
        Integer end();

        static Range of(int start, Integer end) {
            if (end != null && end < start) {
                throw new IllegalArgumentException("End (" + end + ") less then start (" + start + ")");
            }
            return ImmutableRange.of(start, end);
        }
    }

    public static byte[] remove(byte[] ab, List<Range> ranges) {
        return remove(ab, ab.length, ranges);
    }

    public static byte[] remove(byte[] ab, int abLen, List<Range> ranges) {
        // TODO: normalize/order ranges...
        int cutLen = ranges.stream()
                .map(r -> Objects.requireNonNull(r.end()) - r.start() + 1)
                .reduce(0, Integer::sum);

        int newLen = abLen - cutLen;
        if (newLen == ab.length && newLen == abLen) {
            return ab;
        }

        if (newLen == 0) {
            return null;
        }

        byte[] result = new byte[newLen];
        int srcPos = 0;
        int dstPos = 0;
        for (Range r : ranges) {
            int len = r.start() - srcPos;
            System.arraycopy(ab, srcPos, result, dstPos, len);
            srcPos = Objects.requireNonNull(r.end()) + 1;
            dstPos += len;
        }
        if (dstPos < result.length) {
            System.arraycopy(ab, srcPos, result, dstPos, abLen - srcPos);
        }
        return result;
    }

    public static List<Range> partialIndexOfAll(byte[] ab, byte[] startPattern, byte[] endPattern) {
        return partialIndexOfAll(ab, ab.length, startPattern, startPattern.length, endPattern, endPattern.length);
    }

    public static List<Range> partialIndexOfAll(byte[] ab, int abLen,
                                                byte[] startPattern, int startPatternLen,
                                                byte[] endPattern, int endPatternLen) {
        List<Range> result = new ArrayList<>();

        int from = 0;
        while (true) {
            int start = Bytes.partialIndexOf(ab, abLen, startPattern, startPatternLen, from);
            if (start < 0) {
                break;
            }

            if (start + startPatternLen > abLen) {
                // partial start match
                result.add(Range.of(start, null));
                break;
            }

            int end = Bytes.partialIndexOf(ab, abLen, endPattern, endPatternLen, start);
            if (end < 0) {
                // only start match
                result.add(Range.of(start, null));
                break;
            }

            result.add(Range.of(start, end));

            from = end + endPatternLen;
        }
        return result;
    }

    public static int partialIndexOf(byte[] ab, byte[] pattern) {
        return partialIndexOf(ab, ab.length, pattern, pattern.length);
    }

    public static int partialIndexOf(byte[] ab, int abLen, byte[] pattern, int patternLen) {
        return partialIndexOf(ab, abLen, pattern, patternLen, 0);
    }

    public static int partialIndexOf(byte[] ab, int abLen, byte[] pattern, int patternLen, int from) {
        for (int i = from; i < abLen; i++) {
            boolean found = true;
            for (int j = 0; j < patternLen; j++) {
                if (ab[i + j] != pattern[j]) {
                    found = false;
                    break;
                }
                if (i + j + 1 >= abLen) {
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }


    private Bytes() {
    }
}
