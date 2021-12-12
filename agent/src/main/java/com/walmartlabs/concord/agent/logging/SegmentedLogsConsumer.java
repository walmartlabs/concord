//package com.walmartlabs.concord.agent.logging;
//
//import com.walmartlabs.concord.client.LogSegmentUpdateRequest;
//
//import java.nio.ByteBuffer;
//import java.util.*;
//import java.util.function.Consumer;
//
//import static com.walmartlabs.concord.agent.logging.SegmentHeaderParser.*;
//
//public class SegmentedLogsConsumer implements Consumer<RedirectedProcessLog.Chunk> {
//
//    private static final byte[] EMPTY = new byte[0];
//
//    private final UUID instanceId;
//    private final LogAppender logAppender;
//
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
//        unparsed = EMPTY;
//
//        Map<Long, List<HeaderLocation>> headers = new HashMap<>();
//        int pos = SegmentHeaderParser.parse(ab, headers);
//        for (Map.Entry<Long, List<HeaderLocation>> e : headers.entrySet()) {
//            int buffLength = e.getValue().stream().mapToInt(h -> h.header().actualLength()).sum();
//            byte[] segmentBuffer = new byte[buffLength];
//            fillBuffer(e.getValue(), ab, segmentBuffer);
//
//            // TODO: retry?
//            logAppender.appendLog(instanceId, e.getKey(), ab);
//
//            Header stats = findStats(e.getValue());
//            if (stats != null) {
//                logAppender.updateSegment(instanceId, e.getKey(), LogSegmentStats.builder()
//                        .status(stats.done() ? LogSegmentUpdateRequest.StatusEnum.OK : LogSegmentUpdateRequest.StatusEnum.FAILED)
//                        .warnings(stats.warnCount())
//                        .errors(stats.errorCount())
//                        .build());
//            }
//        }
//
//        HeaderLocation partialSegment = findPartialHeader(headers);
//        if (partialSegment != null) {
//            unparsed = SegmentHeaderParser.serialize(partialSegment.header()); // length = originalLength - actualLength
//        }
//
//        if (pos < ab.length) {
//            if (unparsed != EMPTY) {
//                throw new RuntimeException("Unexpected partial segment and unparsed tail");
//            }
//
//            unparsed = Arrays.copyOfRange(ab, pos, ab.length);
//        }
//    }
//
//    private static void fillBuffer(List<HeaderLocation> value, byte[] ab, byte[] segmentBuffer) {
//    }
//
//    private static HeaderLocation findPartialHeader(Map<Long, List<HeaderLocation>> headers) {
//        HeaderLocation result = null;
//        for (Map.Entry<Long, List<HeaderLocation>> e : headers.entrySet()) {
//            for (HeaderLocation segment : e.getValue()) {
//                if (segment.header().actualLength() != segment.header().length()) {
//                    if (result != null) {
//                        throw new RuntimeException("Unexpected second partial segment");
//                    }
//                    result = segment;
//                }
//            }
//        }
//        return result;
//    }
//}
