package com.walmartlabs.concord.console3;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.walmartlabs.concord.console3.resources.ConsoleResource;
import com.walmartlabs.concord.console3.resources.UserProfileResource;
import com.walmartlabs.concord.server.boot.FilterChainConfigurator;
import com.walmartlabs.concord.server.sdk.rest.Component;

import javax.inject.Named;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindApiDescriptor;
import static com.walmartlabs.concord.server.Utils.bindJaxRsResource;

@Named
public class Console3Module implements Module {

    @Override
    public void configure(Binder binder) {
        newSetBinder(binder, FilterChainConfigurator.class).addBinding().to(ConsoleFilterChainConfigurator.class);

        binder.bind(TemplateWriter.class).in(SINGLETON);
        newSetBinder(binder, Component.class).addBinding().to(TemplateWriter.class);

        bindJaxRsResource(binder, ConsoleResource.class);
        bindJaxRsResource(binder, UserProfileResource.class);
        bindApiDescriptor(binder, Console3ApiDescriptor.class);
    }
}
