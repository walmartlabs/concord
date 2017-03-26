package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.process.Payload;

public class Chain {

    private final PayloadProcessor[] processors;
    private final int current;

    public Chain(PayloadProcessor... processors) {
        this(processors, 0);
    }

    private Chain(PayloadProcessor[] processors, int current) {
        this.processors = processors;
        this.current = current;
    }

    public Payload process(Payload payload) {
        if (current >= processors.length) {
            return payload;
        }

        PayloadProcessor p = processors[current];
        Chain next = new Chain(processors, current + 1);

        return p.process(next, payload);
    }
}
