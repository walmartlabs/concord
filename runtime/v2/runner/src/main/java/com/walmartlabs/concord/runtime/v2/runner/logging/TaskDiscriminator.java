package com.walmartlabs.concord.runtime.v2.runner.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.sift.AbstractDiscriminator;
import com.walmartlabs.concord.runtime.v2.runner.vm.TaskThreadGroup;

public class TaskDiscriminator extends AbstractDiscriminator<ILoggingEvent> {

    /**
     * The segment ID lookup's maximum depth (limits nesting of {@link ThreadGroup}s).
     */
    private static final int MAX_DEPTH = 100;

    @Override
    public String getDiscriminatingValue(ILoggingEvent iLoggingEvent) {
        int depth = 0;

        ThreadGroup g = Thread.currentThread().getThreadGroup();
        while (true) {
            if (g instanceof TaskThreadGroup) {
                return ((TaskThreadGroup) g).getSegmentId();
            }

            if (g.getParent() == null) {
                break;
            }

            g = g.getParent();
            depth++;

            if (depth >= MAX_DEPTH) {
                throw new IllegalStateException("Maximum ThreadGroup nesting limit is reached. " +
                        "This is most likely a bug in the runtime and/or a plugin.");
            }
        }
        return "system";
    }

    @Override
    public String getKey() {
        return "_concord_segment";
    }
}
