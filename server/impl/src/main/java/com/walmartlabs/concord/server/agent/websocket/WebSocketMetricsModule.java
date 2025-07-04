package com.walmartlabs.concord.server.agent.websocket;

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

import com.codahale.metrics.Gauge;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.server.message.MessageChannelManager;
import com.walmartlabs.concord.server.queueclient.message.MessageType;
import com.walmartlabs.concord.server.sdk.metrics.GaugeProvider;

import javax.inject.Provider;

public class WebSocketMetricsModule extends AbstractModule {

    @Override
    protected void configure() {
        Provider<MessageChannelManager> channelManagerProvider = getProvider(MessageChannelManager.class);

        @SuppressWarnings("rawtypes")
        Multibinder<GaugeProvider> gauges = Multibinder.newSetBinder(binder(), GaugeProvider.class);
        gauges.addBinding().toInstance(createGauge(channelManagerProvider));
        gauges.addBinding().toInstance(create(channelManagerProvider));
    }

    private static GaugeProvider<Integer> createGauge(Provider<MessageChannelManager> channelManagerProvider) {
        return new GaugeProvider<>() {
            @Override
            public String name() {
                return "websocket-clients";
            }

            @Override
            public Gauge<Integer> gauge() {
                return () -> channelManagerProvider.get().connectedClientsCount();
            }
        };
    }

    private static GaugeProvider<Integer> create(Provider<MessageChannelManager> channelManagerProvider) {
        return new GaugeProvider<>() {
            @Override
            public String name() {
                return "agent-workers-available";
            }

            @Override
            public Gauge<Integer> gauge() {
                return () -> channelManagerProvider.get().getRequests(MessageType.PROCESS_REQUEST).size();
            }
        };
    }
}
