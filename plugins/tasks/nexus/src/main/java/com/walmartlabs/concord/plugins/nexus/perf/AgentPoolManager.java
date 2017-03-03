package com.walmartlabs.concord.plugins.nexus.perf;

import com.sonatype.nexus.perftest.controller.Agent;
import com.sonatype.nexus.perftest.controller.AgentPool;
import com.sonatype.nexus.perftest.controller.Swarm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class AgentPoolManager {

    private static final Logger log = LoggerFactory.getLogger(AgentPoolManager.class);
    private static final long DEFAULT_POLLING_INTERVAL = 5000;

    private final AgentPool pool;
    private final List<Agent> loaded;

    public AgentPoolManager(AgentPool pool) {
        this.pool = pool;
        this.loaded = new ArrayList<>();
    }

    public void load(String scenario, int count, Map<String, String> overrides) {
        synchronized (loaded) {
            log.info("Acquiring {} agent connection(s)...", count);
            Collection<Agent> agents = pool.acquire(count);
            if (agents.size() != count) {
                throw new IllegalStateException("Can't acquire enough agents to continue. Required: " + count + ", got: " + agents.size());
            }

            agents.parallelStream().forEach(a -> {
                try {
                    a.abort();
                    a.load(scenario, overrides);
                } catch (Exception e) {
                    log.error("Agent '{}': error while loading scenario '{}'", a, scenario, e);
                    throw e;
                }
            });
            loaded.addAll(agents);
        }
    }

    public void start() {
        synchronized (loaded) {
            loaded.parallelStream().forEach(a -> a.start());
        }
    }

    public void release() {
        pool.releaseAll();
    }

    public void waitToStart(long timeout) {
        List<Agent> agents;
        synchronized (loaded) {
            log.info("Waiting for {} nexus-perf agent(s) to start... (timeout {}s)", loaded.size(), TimeUnit.SECONDS.convert(timeout, TimeUnit.MILLISECONDS));
            agents = new ArrayList<>(loaded);
        }

        waitFor(agents, timeout, (a) -> a.getControlBean().isRunning());

        log.info("Agents started");
    }

    public void waitToFinish(long timeout, boolean stopOnFailure) {
        List<Agent> agents;
        synchronized (loaded) {
            log.info("Waiting for {} nexus-perf agent(s) to finish... (timeout {}s)", loaded.size(), TimeUnit.SECONDS.convert(timeout, TimeUnit.MILLISECONDS));
            agents = new ArrayList<>(loaded);
        }

        long t1 = System.currentTimeMillis();
        while (!agents.isEmpty()) {
            try {
                Thread.sleep(DEFAULT_POLLING_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            long failures = 0;

            // TODO refactor as a conditional
            for (Iterator<Agent> i = agents.iterator(); i.hasNext(); ) {
                Agent a = i.next();
                if (!a.getControlBean().isRunning()) {
                    i.remove();
                }

                long fs = countFailures(a);
                failures += fs;

                if (stopOnFailure && fs > 0) {
                    log.info("Agent {}, {} error(s)", a, fs);
                    return;
                }
            }

            long t2 = System.currentTimeMillis();
            if (t2 - t1 >= timeout) {
                throw new RuntimeException("Timeout waiting for agents: " + agents);
            }

            log.info("Waiting for {} agent(s), {} failure(s)...", agents.size(), failures);
        }

        log.info("Agents finished");
    }

    private static void waitFor(List<Agent> agents, long timeout, Function<Agent, Boolean> condition) {
        long t1 = System.currentTimeMillis();
        while (!agents.isEmpty()) {
            try {
                Thread.sleep(DEFAULT_POLLING_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            for (Iterator<Agent> i = agents.iterator(); i.hasNext(); ) {
                Agent a = i.next();
                if (condition.apply(a)) {
                    i.remove();
                }
            }

            long t2 = System.currentTimeMillis();
            if (t2 - t1 >= timeout) {
                throw new RuntimeException("Timeout waiting for agents: " + agents);
            }
        }
    }

    public boolean hasFailures() {
        boolean result = false;

        synchronized (loaded) {
            for (Agent a : loaded) {
                for (Swarm s : a.getSwarms()) {
                    List<String> failures = s.getControl().getFailures();
                    if (failures != null && !failures.isEmpty()) {
                        StringBuilder b = new StringBuilder();
                        for (String f : failures) {
                            b.append(f).append("\n");
                        }

                        log.warn("{} failed with:\n{}\n======================================================", a, b);

                        result = true;
                    }
                }
            }
        }

        if (!result) {
            log.info("No failures were reported");
        }

        return result;
    }

    private static long countFailures(Agent a) {
        long cnt = 0;
        for (Swarm s : a.getSwarms()) {
            cnt += s.getControl().getFailuresCount();
        }
        return cnt;
    }
}
