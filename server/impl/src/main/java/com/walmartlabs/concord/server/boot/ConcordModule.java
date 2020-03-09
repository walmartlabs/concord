package com.walmartlabs.concord.server.boot;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import com.walmartlabs.concord.db.DatabaseModule;
import com.walmartlabs.concord.server.metrics.MetricModule;
import org.sonatype.siesta.server.resteasy.ResteasyModule;
import org.sonatype.siesta.server.validation.ValidationModule;

import javax.inject.Named;

@Named
public class ConcordModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new ConfigurationModule());

        install(new MetricModule());

        install(new DatabaseModule());

        install(new ResteasyModule());

        install(new ValidationModule());
    }
}
