package com.walmartlabs.concord.agent.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import com.walmartlabs.concord.runtime.common.logger.ImmutableLogSegmentHeader;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentDeserializer;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentHeader;
import org.immutables.value.Value;

import java.nio.ByteBuffer;
import java.util.List;

public class SegmentHeaderParser {

    private static final int MAX_FIELD_BYTES = String.valueOf(Long.MAX_VALUE).getBytes().length;

    // msgLength|segmentId|status|warnings|errors|msg

    public static int parse(byte[] ab, List<Segment> segments, List<Position> invalidSegments) {
        Field field = Field.MSG_LENGTH;
        StringBuilder fieldData = new StringBuilder();
        int mark = -1;
        ImmutableLogSegmentHeader.Builder headerBuilder = LogSegmentHeader.builder();

        boolean continueParse = true;
        State state = State.FIND_HEADER;
        ByteBuffer bb = ByteBuffer.wrap(ab);
        while (continueParse) {
            switch (state) {
                case FIND_HEADER: {
                    if (bb.remaining() <= 0) {
                        continueParse = false;
                        break;
                    }

                    char ch = (char) bb.get();
                    if (ch == '|') {
                        if (mark != -1) {
                            invalidSegments.add(Position.of(mark, bb.position() - 1));
                        }

                        mark = bb.position() - 1;
                        state = State.FIELD_DATA;
                    } else {
                        if (mark == -1) {
                            mark = bb.position() - 1;
                        }
                    }
                    break;
                }
                case FIELD_DATA: {
                    if (bb.remaining() <= 0) {
                        continueParse = false;
                        break;
                    }

                    char ch = (char)bb.get();
                    if (ch == '|') {
                        state = State.END_FIELD;
                        break;
                    }

                    if (fieldData.length() > MAX_FIELD_BYTES || !Character.isDigit(ch)) {
                        // reset
                        fieldData.setLength(0);
                        field = Field.MSG_LENGTH;
                        state = State.FIND_HEADER;
                        break;
                    }

                    fieldData.append(ch);
                    break;
                }
                case END_FIELD: {
                    String fieldValue = fieldData.toString();
                    if (fieldData.isEmpty()) {
                        // reset
                        field = Field.MSG_LENGTH;
                        state = State.FIND_HEADER;
                        bb.position(bb.position() - 1);
                        break;
                    }

                    field.process(fieldValue, headerBuilder);

                    field = field.next();
                    if (field == null) {
                        LogSegmentHeader h = headerBuilder.build();
                        segments.add(Segment.of(h, bb.position()));

                        int actualLength = Math.min(h.length(), bb.remaining());
                        bb.position(bb.position() + actualLength);

                        // reset
                        field = Field.MSG_LENGTH;
                        mark = -1;

                        state = State.FIND_HEADER;
                    } else {
                        state = State.FIELD_DATA;
                    }

                    fieldData.setLength(0);

                    break;
                }
            }
        }

        int result;
        if (mark != -1) {
            if (state == State.FIND_HEADER) {
                invalidSegments.add(Position.of(mark, bb.position()));
                result = bb.position();
            } else {
                result = mark;
            }
        } else {
            result = bb.position();
        }

        return result;
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    public interface Position {

        @Value.Parameter
        int start();

        @Value.Parameter
        int end();

        static Position of(int start, int end) {
            return ImmutablePosition.of(start, end);
        }
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    public interface Segment {

        @Value.Parameter
        LogSegmentHeader header();

        @Value.Parameter
        int msgStart();

        static Segment of(LogSegmentHeader header, int msgStart) {
            return ImmutableSegment.of(header, msgStart);
        }
    }

    enum State {
        FIND_HEADER,
        FIELD_DATA,
        END_FIELD
    }

    enum Field {
        MSG_LENGTH {
            @Override
            public Field next() {
                return SEGMENT_ID;
            }

            @Override
            public void process(String fieldValue, ImmutableLogSegmentHeader.Builder headerBuilder) {
                headerBuilder.length(Integer.parseInt(fieldValue));
            }
        },

        SEGMENT_ID {
            @Override
            public Field next() {
                return STATUS;
            }

            @Override
            public void process(String fieldValue, ImmutableLogSegmentHeader.Builder headerBuilder) {
                headerBuilder.segmentId(Long.parseLong(fieldValue));
            }
        },

        STATUS {
            @Override
            public Field next() {
                return WARNINGS;
            }

            @Override
            public void process(String fieldValue, ImmutableLogSegmentHeader.Builder headerBuilder) {
                headerBuilder.status(LogSegmentDeserializer.deserializeStatus(fieldValue));
            }
        },

        WARNINGS {
            @Override
            public Field next() {
                return ERRORS;
            }

            @Override
            public void process(String fieldValue, ImmutableLogSegmentHeader.Builder headerBuilder) {
                headerBuilder.warnCount(Integer.parseInt(fieldValue));
            }
        },

        ERRORS {
            @Override
            public Field next() {
                return null;
            }

            @Override
            public void process(String fieldValue, ImmutableLogSegmentHeader.Builder headerBuilder) {
                headerBuilder.errorCount(Integer.parseInt(fieldValue));
            }
        };

        public abstract Field next();

        public abstract void process(String fieldValue, ImmutableLogSegmentHeader.Builder headerBuilder);
    }
}
