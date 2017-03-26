package com.walmartlabs.concord.plugins.nexus.perf2;

import com.google.common.base.Throwables;
import com.walmartlabs.concord.agent.api.JobStatus;
import com.walmartlabs.concord.agent.api.JobType;
import com.walmartlabs.concord.agent.pool.AgentConnection;
import com.walmartlabs.concord.agent.pool.AgentPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static java.util.stream.IntStream.range;

public class PerfSession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PerfSession.class);

    private static final long ACQUIRE_TIMEOUT = 10000;
    private static final JobType DEFAULT_JOB_TYPE = JobType.JUNIT_GROOVY;
    private static final String DEFAULT_ENTRY_POINT = "scenario.groovy";

    public static PerfSession create(AgentPoolFactory agentPoolFactory, Collection<URI> hosts) {
        UUID sessionId = UUID.randomUUID();
        AgentPool pool = agentPoolFactory.create(hosts);
        return new PerfSession(sessionId, pool);
    }

    private final UUID id;
    private final AgentPool pool;

    private final Collection<AgentConnection> connections = new ArrayList<>();
    private final Map<AgentConnection, String> jobs = new HashMap<>();
    private final Object lock = new Object();

    private PerfSession(UUID id, AgentPool pool) {
        this.id = id;
        this.pool = pool;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public void close() {
        Collection<AgentConnection> connections;
        Map<AgentConnection, String> jobs;

        synchronized (lock) {
            connections = new ArrayList<>(this.connections);
            jobs = new HashMap<>(this.jobs);

            this.connections.clear();
            this.jobs.clear();
        }

        connections.parallelStream().forEach(a -> a.cancel(jobs.get(a), false));

        pool.close();
    }

    public void loadAndStart(File payload, Collection<Map<String, Object>> scenarios) {
        if (scenarios.isEmpty()) {
            throw new IllegalArgumentException("No scenarios defined");
        }

        for (Map<String, Object> scenario : scenarios) {
            String name = getString(scenario, "name", "Scenario name is required");
            int agentCount = getInt(scenario, "agents", "Agent count must be specified");

            List<AgentConnection> conns = range(0, agentCount).parallel()
                    .mapToObj(this::acquire)
                    .collect(Collectors.toList());

            try {
                Map<AgentConnection, String> scenarioJobs = new ConcurrentHashMap<>();
                conns.parallelStream().forEach(a -> {
                    String jobId = run(name, a, payload);
                    scenarioJobs.put(a, jobId);
                });

                synchronized (lock) {
                    connections.addAll(conns);
                    jobs.putAll(scenarioJobs);
                }
            } catch (Exception e) {
                conns.parallelStream().forEach(AgentConnection::cancelAll);
                throw e;
            }
        }
    }

    public void waitToFinish(long timeout) throws TimeoutException {
        Collection<AgentConnection> agents;
        synchronized (lock) {
            agents = new ArrayList<>(connections);
        }

        long t1 = System.currentTimeMillis();
        while (!agents.isEmpty() && !Thread.currentThread().isInterrupted()) {
            try {
                log.info("Waiting for {} agent(s) to finish...", agents.size());
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            for (Iterator<AgentConnection> i = agents.iterator(); i.hasNext(); ) {
                JobStatus s = status(i.next());
                if (s != JobStatus.RUNNING) {
                    i.remove();
                }
            }

            long t2 = System.currentTimeMillis();
            if (t2 - t1 >= timeout) {
                throw new TimeoutException("Timeout waiting for agents to finish");
            }
        }

        log.info("All agents are finished");
    }

    private AgentConnection acquire(int i) {
        return pool.getConnection(ACQUIRE_TIMEOUT);
    }

    private JobStatus status(AgentConnection r) {
        String jobId;
        synchronized (lock) {
            jobId = jobs.get(r);
        }

        JobStatus s = r.getStatus(jobId);
        log.info("Job '{}' is {}", jobId, s);
        return s;
    }

    private static String run(String name, AgentConnection r, File payload) {
        String id = UUID.randomUUID().toString();
        try (InputStream in = new BufferedInputStream(new FileInputStream(payload))) {
            String entryPoint = name + "/" + DEFAULT_ENTRY_POINT;
            r.start(id, DEFAULT_JOB_TYPE, entryPoint, in);
            return id;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private static String getString(Map<String, Object> m, String k, String messageIfNull) {
        Object v = m.get(k);
        if (v == null) {
            throw new IllegalArgumentException(messageIfNull);
        }
        if (v instanceof String) {
            return (String) v;
        }
        throw new IllegalArgumentException("Invalid value type: expected 'String', got '" + v.getClass() + "'");
    }

    private static int getInt(Map<String, Object> m, String k, String messageIfNull) {
        Object v = m.get(k);
        if (v == null) {
            throw new IllegalArgumentException(messageIfNull);
        }
        if (v instanceof Integer) {
            return (Integer) v;
        }
        throw new IllegalArgumentException("Invalid value type: expected 'int', got '" + v.getClass() + "'");
    }
}
