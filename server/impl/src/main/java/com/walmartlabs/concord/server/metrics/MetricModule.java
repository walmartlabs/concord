package com.walmartlabs.concord.server.metrics;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;

import javax.inject.Named;

@Named
public class MetricModule extends AbstractModule {

    @Override
    protected void configure() {
        MetricInterceptor i = new MetricInterceptor();
        requestInjection(i);
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(WithTimer.class), i);

        bindListener(Matchers.any(), new MetricTypeListener());
    }
}
