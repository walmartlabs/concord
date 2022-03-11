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

import org.immutables.value.Value;

import java.nio.ByteBuffer;
import java.util.List;

public class SegmentHeaderParser {

    private static final int MAX_FIELD_BYTES = String.valueOf(Long.MAX_VALUE).getBytes().length;

    // msgLength|segmentId|DONE?|warnings|errors|msg

    public static int parse(byte[] ab, List<Segment> segments, List<Position> invalidSegments) {
        Field field = Field.MSG_LENGTH;
        StringBuilder fieldData = new StringBuilder();
        int mark = -1;
        ImmutableHeader.Builder headerBuilder = Header.builder();

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
                    if (fieldData.length() == 0) {
                        // reset
                        field = Field.MSG_LENGTH;
                        state = State.FIND_HEADER;
                        bb.position(bb.position() - 1);
                        break;
                    }

                    field.process(fieldValue, headerBuilder);

                    field = field.next();
                    if (field == null) {
                        Header h = headerBuilder.build();
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

    public static byte[] serialize(Header header) {
        return String.format("|%d|%d|%s|%d|%d|", header.length(), header.segmentId(), (header.done() ? '1' : '0'), header.warnCount(), header.errorCount()).getBytes();
    }

    @Value.Immutable
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
    public interface Segment {

        @Value.Parameter
        Header header();

        @Value.Parameter
        int msgStart();

        static Segment of(Header header, int msgStart) {
            return ImmutableSegment.of(header, msgStart);
        }
    }

    @Value.Immutable
    public interface Header {

        int length();

        long segmentId();

        int warnCount();

        int errorCount();

        boolean done();

        static ImmutableHeader.Builder builder() {
            return ImmutableHeader.builder();
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
            public void process(String fieldValue, ImmutableHeader.Builder headerBuilder) {
                headerBuilder.length(Integer.parseInt(fieldValue));
            }
        },

        SEGMENT_ID {
            @Override
            public Field next() {
                return DONE;
            }

            @Override
            public void process(String fieldValue, ImmutableHeader.Builder headerBuilder) {
                headerBuilder.segmentId(Long.parseLong(fieldValue));
            }
        },

        DONE {
            @Override
            public Field next() {
                return WARNINGS;
            }

            @Override
            public void process(String fieldValue, ImmutableHeader.Builder headerBuilder) {
                headerBuilder.done("1".equals(fieldValue));
            }
        },

        WARNINGS {
            @Override
            public Field next() {
                return ERRORS;
            }

            @Override
            public void process(String fieldValue, ImmutableHeader.Builder headerBuilder) {
                headerBuilder.warnCount(Integer.parseInt(fieldValue));
            }
        },

        ERRORS {
            @Override
            public Field next() {
                return null;
            }

            @Override
            public void process(String fieldValue, ImmutableHeader.Builder headerBuilder) {
                headerBuilder.errorCount(Integer.parseInt(fieldValue));
            }
        };

        public abstract Field next();

        public abstract void process(String fieldValue, ImmutableHeader.Builder headerBuilder);
    }
}
