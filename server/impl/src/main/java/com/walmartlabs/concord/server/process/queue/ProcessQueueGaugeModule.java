package com.walmartlabs.concord.server.process.queue;

import com.codahale.metrics.Gauge;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.server.metrics.GaugeProvider;
import com.walmartlabs.concord.server.process.ProcessStatus;

import javax.inject.Named;

@Named
public class ProcessQueueGaugeModule extends AbstractModule {

    @Override
    protected void configure() {
        Provider<ProcessQueueDao> queueDaoProvider = getProvider(ProcessQueueDao.class);

        // TODO use a single query to fetch all statuses + derivative gauges?
        Multibinder<GaugeProvider> tasks = Multibinder.newSetBinder(binder(), GaugeProvider.class);
        for (ProcessStatus s : ProcessStatus.values()) {
            tasks.addBinding().toInstance(create(queueDaoProvider, s));
        }
    }

    private static GaugeProvider<Integer> create(Provider<ProcessQueueDao> queueDaoProvider, ProcessStatus status) {
        return new GaugeProvider<Integer>() {
            @Override
            public String name() {
                return "process-queue-" + status.toString().toLowerCase();
            }

            @Override
            public Gauge<Integer> gauge() {
                return () -> queueDaoProvider.get().count(status);
            }
        };
    }
}
