package com.walmartlabs.concord.runtime.v2.runner.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.walmartlabs.concord.runtime.v2.runner.SensitiveDataHolder;

import java.util.Collection;

public class MaskingSensitiveDataLayout extends PatternLayout {

    @Override
    public String doLayout(ILoggingEvent event) {
        Collection<String> sensitiveData = SensitiveDataHolder.getInstance().get();
        String msg = super.doLayout(event);
        for (String d : sensitiveData) {
            msg = msg.replace(d, "******");
        }
        return msg;
    }
}
