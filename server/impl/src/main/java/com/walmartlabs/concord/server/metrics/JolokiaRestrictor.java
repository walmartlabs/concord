package com.walmartlabs.concord.server.metrics;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jolokia.restrictor.AbstractConstantRestrictor;

public class JolokiaRestrictor extends AbstractConstantRestrictor {

    public JolokiaRestrictor() {
        super(true);
    }

    @Override
    public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
        Subject subject = SecurityUtils.getSubject();
        if (subject == null) {
            return false;
        }

        return subject.isPermitted("metrics");
    }
}
