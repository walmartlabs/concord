package com.walmartlabs.concord.agent.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import javax.inject.Inject;
import java.util.Set;

public class CompositeProcessLogTransport implements ProcessLogTransport {

    private final Set<ProcessLogTransport> transports;

    @Inject
    public CompositeProcessLogTransport(Set<ProcessLogTransport> transports) {
        this.transports = transports;
    }

    @Override
    public DeliveryStatus appendSystem(byte[] bytes) {
        return transports.stream()
                .map(transport -> transport.appendSystem(bytes))
                .reduce(DeliveryStatus.DELIVERED, CompositeProcessLogTransport::combine);
    }

    @Override
    public DeliveryStatus appendSegment(long segmentId, byte[] bytes) {
        return transports.stream()
                .map(transport -> transport.appendSegment(segmentId, bytes))
                .reduce(DeliveryStatus.DELIVERED, CompositeProcessLogTransport::combine);
    }

    @Override
    public DeliveryStatus updateSegment(long segmentId, LogSegmentStats stats) {
        return transports.stream()
                .map(transport -> transport.updateSegment(segmentId, stats))
                .reduce(DeliveryStatus.DELIVERED, CompositeProcessLogTransport::combine);
    }

    private static DeliveryStatus combine(DeliveryStatus current, DeliveryStatus next) {
        if (current == DeliveryStatus.FAILED || next == DeliveryStatus.FAILED) {
            return DeliveryStatus.FAILED;
        }
        return DeliveryStatus.DELIVERED;
    }
}
