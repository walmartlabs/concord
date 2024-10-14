package com.walmartlabs.concord.server.events;

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
import com.walmartlabs.concord.server.events.externalevent.ExternalEventTriggerProcessor;
import com.walmartlabs.concord.server.events.externalevent.ExternalEventTriggerV1Processor;
import com.walmartlabs.concord.server.events.externalevent.ExternalEventTriggerV2Processor;
import com.walmartlabs.concord.server.events.github.GithubTriggerProcessor;
import com.walmartlabs.concord.server.sdk.events.ProcessEventListener;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindJaxRsResource;

public class EventModule implements Module {

    @Override
    public void configure(Binder binder) {
        newSetBinder(binder, ExternalEventTriggerProcessor.class).addBinding().to(ExternalEventTriggerV1Processor.class);
        newSetBinder(binder, ExternalEventTriggerProcessor.class).addBinding().to(ExternalEventTriggerV2Processor.class);

        binder.bind(TriggerProcessExecutor.class).in(SINGLETON);

        newSetBinder(binder, ProcessEventListener.class);

        binder.bind(GithubTriggerProcessor.class).in(SINGLETON);
        newSetBinder(binder, GithubTriggerProcessor.EventEnricher.class).addBinding().to(GithubTriggerProcessor.RepositoryInfoEnricher.class);

        bindJaxRsResource(binder, ExternalEventResource.class);
        bindJaxRsResource(binder, GithubEventResource.class);
    }
}
