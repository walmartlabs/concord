package com.walmartlabs.concord.server.plugins.eventsink.kafka;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.server.sdk.BackgroundTask;
import com.walmartlabs.concord.server.sdk.audit.AuditLogListener;
import com.walmartlabs.concord.server.sdk.events.ProcessEventListener;
import com.walmartlabs.concord.server.sdk.log.ProcessLogListener;

import javax.inject.Named;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

@Named
public class KafkaModule implements Module {

    @Override
    public void configure(Binder binder) {
        binder.bind(KafkaEventSinkConfiguration.class).in(SINGLETON);

        binder.bind(KafkaEventSink.class).in(SINGLETON);
        newSetBinder(binder, ProcessEventListener.class).addBinding().to(KafkaEventSink.class);
        newSetBinder(binder, ProcessLogListener.class).addBinding().to(KafkaEventSink.class);
        newSetBinder(binder, AuditLogListener.class).addBinding().to(KafkaEventSink.class);

        binder.bind(KafkaConnector.class).in(SINGLETON);
        newSetBinder(binder, BackgroundTask.class).addBinding().to(KafkaConnector.class);
    }
}
