package com.walmartlabs.concord.runtime.v2.runner.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.sift.AbstractDiscriminator;
import com.walmartlabs.concord.runtime.v2.runner.vm.TaskCallCommand;

public class TaskDiscriminator extends AbstractDiscriminator<ILoggingEvent> {

    @Override
    public String getDiscriminatingValue(ILoggingEvent iLoggingEvent) {
        ThreadGroup g = Thread.currentThread().getThreadGroup();
        while (true) {
            if (g instanceof TaskCallCommand.TaskThreadGroup) {
                return ((TaskCallCommand.TaskThreadGroup) g).getSegmentId();
            }

            if (g.getParent() == null) {
                break;
            }

            g = g.getParent();
        }
        return "system";
    }

    @Override
    public String getKey() {
        return "_concord_segment";
    }
}
