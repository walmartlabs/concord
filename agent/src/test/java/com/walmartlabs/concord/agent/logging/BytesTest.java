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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.agent.logging.Bytes.Range;
import static org.junit.jupiter.api.Assertions.*;

public class BytesTest {

    @Test
    public void testIndexOf() {
        byte[] ab = {1, 2, 3, 4};
        assertEquals(0, Bytes.partialIndexOf(ab, new byte[]{1, 2}));
        assertEquals(1, Bytes.partialIndexOf(ab, new byte[]{2, 3}));
        assertEquals(2, Bytes.partialIndexOf(ab, new byte[]{3, 4, 5}));
        assertEquals(3, Bytes.partialIndexOf(ab, new byte[]{4}));
        assertEquals(3, Bytes.partialIndexOf(ab, new byte[]{4, 5}));
        assertEquals(3, Bytes.partialIndexOf(ab, new byte[]{4, 5, 6, 7, 8}));
    }

    @Test
    public void testPartialIndexOfAll() {
        byte[] sp = {'<', '<'};
        byte[] ep = {'>', '>'};

        {
            byte[] ab = {'<', '<', 1, 2, 3, '>', '>'};

            assertEquals(Collections.singletonList(Range.of(0, 5)),
                    Bytes.partialIndexOfAll(ab, sp, ep));

            assertNull(Bytes.remove(ab, normalize(Bytes.partialIndexOfAll(ab, sp, ep), ab.length, ep.length)));
        }

        {
            byte[] ab = {0, '<', '<', 1, 2, 3, '>', '>'};

            assertEquals(Collections.singletonList(Range.of(1, 6)),
                    Bytes.partialIndexOfAll(ab, sp, ep));

            assertArrayEquals(new byte[]{0},
                    Bytes.remove(ab, normalize(Bytes.partialIndexOfAll(ab, sp, ep), ab.length, ep.length)));
        }

        {
            byte[] ab = {0, '<', '<', 1, 2, 3, '>', '>', 4};

            assertEquals(Collections.singletonList(Range.of(1, 6)),
                    Bytes.partialIndexOfAll(ab, sp, ep));

            assertArrayEquals(new byte[]{0, 4},
                    Bytes.remove(ab, normalize(Bytes.partialIndexOfAll(ab, sp, ep), ab.length, ep.length)));
        }

        {
            byte[] ab = {0, 1, 2, 3, '>', '>', 4};

            assertEquals(0, Bytes.partialIndexOfAll(ab, sp, ep).size());

            assertArrayEquals(ab,
                    Bytes.remove(ab, normalize(Bytes.partialIndexOfAll(ab, sp, ep), ab.length, ep.length)));
        }

        {
            byte[] ab = {0, '<', '<', 1, 2, 3, 4};

            assertEquals(Collections.singletonList(Range.of(1, null)),
                    Bytes.partialIndexOfAll(ab, sp, ep));

            assertArrayEquals(new byte[]{0},
                    Bytes.remove(ab, normalize(Bytes.partialIndexOfAll(ab, sp, ep), ab.length, ep.length)));
        }

        {
            byte[] ab = {0, '<', '<', 1, 2, 3, 4, '>'};

            assertEquals(Collections.singletonList(Range.of(1, 7)),
                    Bytes.partialIndexOfAll(ab, sp, ep));

            assertArrayEquals(new byte[]{0},
                    Bytes.remove(ab, normalize(Bytes.partialIndexOfAll(ab, sp, ep), ab.length, ep.length)));
        }

        {
            byte[] ab = {0, '<', '<', 3, '>', '>', 6, '<', '<', 9, '>'};

            assertEquals(Arrays.asList(Range.of(1, 4), Range.of(7, 10)),
                    Bytes.partialIndexOfAll(ab, sp, ep));

            assertArrayEquals(new byte[]{0, 6},
                    Bytes.remove(ab, normalize(Bytes.partialIndexOfAll(ab, sp, ep), ab.length, ep.length)));
        }

        {
            byte[] ab = {0, '<', '<', 3, '>', '>', 6, '<', '<', 9, '>', '>', 12, 13, 14, 15};

            assertEquals(Arrays.asList(Range.of(1, 4), Range.of(7, 10)),
                    Bytes.partialIndexOfAll(ab, sp, ep));

            assertArrayEquals(new byte[]{0, 6, 12, 13, 14, 15},
                    Bytes.remove(ab, normalize(Bytes.partialIndexOfAll(ab, sp, ep), ab.length, ep.length)));
        }

        {
            byte[] ab = {0, '<', '<', 3, '>', '>', '<', '<', 9, '>', '>'};

            assertEquals(Arrays.asList(Range.of(1, 4), Range.of(6, 9)),
                    Bytes.partialIndexOfAll(ab, sp, ep));

            assertArrayEquals(new byte[]{0},
                    Bytes.remove(ab, normalize(Bytes.partialIndexOfAll(ab, sp, ep), ab.length, ep.length)));
        }
    }

    private static List<Range> normalize(List<Range> ranges, int abLen, int endPatternLen) {
        return ranges.stream()
                .map(r -> Range.of(r.start(), r.end() == null ? abLen - 1 : Math.min(abLen, Objects.requireNonNull(r.end()) + endPatternLen) - 1))
                .collect(Collectors.toList());
    }
}
