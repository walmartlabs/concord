package com.walmartlabs.concord.server.boot.resteasy;

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
import com.walmartlabs.concord.server.sdk.rest.Component;

import javax.servlet.ServletContextListener;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindExceptionMapper;

public class ResteasyModule implements Module {

    @Override
    public void configure(Binder binder) {
        newSetBinder(binder, ApiDescriptor.class).addBinding().to(ConcordApiDescriptor.class);

        newSetBinder(binder, ServletContextListener.class).addBinding().to(ResteasyBootstrapListener.class);

        binder.bind(ObjectMapperContextResolver.class).in(SINGLETON);
        newSetBinder(binder, Component.class).addBinding().to(ObjectMapperContextResolver.class);

        bindExceptionMapper(binder, UnexpectedExceptionMapper.class);
        bindExceptionMapper(binder, WebappExceptionMapper.class);
    }
}
