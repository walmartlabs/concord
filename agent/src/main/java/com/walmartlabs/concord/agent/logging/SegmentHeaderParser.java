//package com.walmartlabs.concord.agent.logging;
//
//import org.immutables.value.Value;
//
//import java.nio.ByteBuffer;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
//public class SegmentHeaderParser {
//
//    private static final int MAX_FIELD_BYTES = String.valueOf(Long.MAX_VALUE).getBytes().length;
//
//    // msgLength|segmentId|DONE?|warnings|errors|msg
//
//    public static int parse(byte[] ab, Map<Long, List<HeaderLocation>> headers) {
//        int result = 0;
//
//        ByteBuffer bb = ByteBuffer.wrap(ab);
//
//        Field[] fields = new Field[5];
//        while (true) {
//            for (int i = 0; i < fields.length; i++) {
//                Field f = nextField(bb);
//                if (f == null) {
//                    return result;
//                } else if (f == Field.INVALID) {
//                    return
//                }
//                fields[i] = f;
//            }
//
//            int length = Integer.parseInt(fields[0].value());
//            int actualLength = Math.min(length, bb.remaining());
//            long segmentId = Long.parseLong(fields[1].value());
//            Header h = Header.builder()
//                    .length(length)
//                    .actualLength(actualLength)
//                    .segmentId(segmentId)
//                    .done(toBoolean(fields[2]))
//                    .warnCount(Integer.parseInt(fields[3].value()))
//                    .errorCount(Integer.parseInt(fields[4].value()))
//                    .build();
//            HeaderLocation hl = HeaderLocation.builder()
//                    .start(fields[0].start())
//                    .end(fields[4].end())
//                    .header(h)
//                    .build();
//
//            headers.computeIfAbsent(segmentId, id -> new ArrayList<>())
//                    .add(hl);
//        }
//
//        return result;
//    }
//
//    private static Field nextField(ByteBuffer bb) {
//        int start = bb.position();
//        int end = indexOf(bb, '|');
//        if (end <= 0) {
//            if (bb.remaining() > MAX_FIELD_BYTES) {
//                return Field.INVALID;
//            }
//
//            return null;
//        } else if (end - start > MAX_FIELD_BYTES) {
//            return Field.INVALID;
//        }
//
//        return Field.of(start, end);
//    }
//
//    private static int indexOf(ByteBuffer bb, char c) {
//        return 0;
//    }
//
//    @Value.Immutable
//    private interface Field {
//
//        static Field INVALID = Field.of(-1, -1, -1);
//
//        @Value.Parameter
//        int start();
//
//        @Value.Parameter
//        int end();
//
//        String value();
//
//        static Field of(int start, int end, String value) {
//            return ImmutableField.of(start, end, value);
//        }
//    }
//
//    public static void main(String[] args) {
//        System.out.println(">>" + (Long.MAX_VALUE));
//        System.out.println(">>" + ("" + Long.MAX_VALUE).getBytes().length);
//
//        System.out.println(">>" + (Integer.MAX_VALUE));
//        System.out.println(">>" + ("" + Integer.MAX_VALUE).getBytes().length);
//    }
//
//    public static byte[] serialize(Header header) {
//        int length = header.length() - header.actualLength();
//
//        return String.format("|%d|%d|%s|%d|%d|", length, header.segmentId(), (header.done() ? '0' : '1'), header.warnCount(), header.errorCount()).getBytes();
//    }
//
//    @Value.Immutable
//    public interface Header {
//
//        int length();
//
//        int actualLength();
//
//        long segmentId();
//
//        int warnCount();
//
//        int errorCount();
//
//        boolean done();
//
//        static ImmutableHeader.Builder builder() {
//            return ImmutableHeader.builder();
//        }
//    }
//
//    @Value.Immutable
//    public interface HeaderLocation {
//
//        int start();
//
//        int end();
//
//        Header header();
//
//        static ImmutableHeaderLocation.Builder builder() {
//            return ImmutableHeaderLocation.builder();
//        }
//    }
//}
