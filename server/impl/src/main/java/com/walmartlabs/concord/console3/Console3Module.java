package com.walmartlabs.concord.console3;

import com.google.inject.Binder;
import com.google.inject.Module;

import javax.inject.Named;

import static com.walmartlabs.concord.server.Utils.bindApiDescriptor;
import static com.walmartlabs.concord.server.Utils.bindJaxRsResource;

@Named
public class Console3Module implements Module {

    @Override
    public void configure(Binder binder) {
        bindJaxRsResource(binder, ConsoleResource.class);
        bindApiDescriptor(binder, Console3ApiDescriptor.class);
    }
}
