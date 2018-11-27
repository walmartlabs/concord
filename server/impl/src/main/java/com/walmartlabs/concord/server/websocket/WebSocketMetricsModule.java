package com.walmartlabs.concord.server.websocket;

import com.codahale.metrics.Gauge;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.server.metrics.GaugeProvider;

import javax.inject.Named;
import javax.inject.Provider;

@Named
public class WebSocketMetricsModule extends AbstractModule {

    @Override
    protected void configure() {
        Provider<WebSocketChannelManager> channelManagerProvider = getProvider(WebSocketChannelManager.class);

        Multibinder<GaugeProvider> gauges = Multibinder.newSetBinder(binder(), GaugeProvider.class);
        gauges.addBinding().toInstance(createGauge(channelManagerProvider));
    }

    private static GaugeProvider<Integer> createGauge(Provider<WebSocketChannelManager> channelManagerProvider) {
        return new GaugeProvider<Integer>() {
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
}
