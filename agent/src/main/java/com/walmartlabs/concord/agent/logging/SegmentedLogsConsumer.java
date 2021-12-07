//package com.walmartlabs.concord.agent.logging;
//
//import java.nio.ByteBuffer;
//import java.util.List;
//import java.util.UUID;
//import java.util.function.Consumer;
//
//public class SegmentedLogsConsumer implements Consumer<RedirectedProcessLog.Chunk> {
//
//    private static final byte[] EMPTY = new byte[0];
//
//    private final UUID instanceId;
//    private final LogAppender logAppender;
//
//    private Long currentSegmentId;
//    private byte[] unparsed = EMPTY;
//
//    public SegmentedLogsConsumer(UUID instanceId, LogAppender logAppender) {
//        this.instanceId = instanceId;
//        this.logAppender = logAppender;
//    }
//
//    @Override
//    public void accept(RedirectedProcessLog.Chunk chunk) {
//        byte[] ab = new byte[unparsed.length + chunk.len()];
//        if (unparsed.length > 0) {
//            System.arraycopy(unparsed, 0, ab, 0, unparsed.length);
//        }
//        System.arraycopy(chunk.bytes(), 0, ab, unparsed.length, chunk.len());
//
//        ByteBuffer bb = ByteBuffer.wrap(ab);
//
//        if (state == HEADER) {
//            List<HeaderLocation> headers = findHeaders(bb);
//
//            // find all headers
//            // collect bytes for each segment
//            // если есть байты для всех headers -> ждём новых headers
//            // если байтов не хватает -> отправляем что есть, прикапываем
//            // текуущий segmentId и сколько ещё байтов нужно.
//        }
//
//        Character.BYTES;
//
//        bb.flip()
//        bb.getChar()
//        bb.remaining()
//
//        logAppender.appendLog(instanceId, ab);
//    }
//
//    private static class Header {
//
//        private final int length;
//        private final long segmentId;
//        private final int warnCount;
//        private final int errorCount;
//    }
//
//    private static class HeaderLocation {
//
//        private final int start;
//        private final int end;
//        private final Header header;
//    }
//}
