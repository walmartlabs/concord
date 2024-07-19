package com.walmartlabs.concord.console3;

import com.walmartlabs.concord.server.boot.resteasy.ApiDescriptor;

public class Console3ApiDescriptor implements ApiDescriptor {

    @Override
    public String[] paths() {
        return new String[]{
                "/console3/*"
        };
    }
}
