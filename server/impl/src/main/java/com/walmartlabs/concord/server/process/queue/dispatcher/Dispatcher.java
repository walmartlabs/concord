package com.walmartlabs.concord.server.process.queue.dispatcher;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.walmartlabs.concord.common.MapMatcher;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.Locks;
import com.walmartlabs.concord.server.PeriodicTask;
import com.walmartlabs.concord.server.cfg.ProcessQueueConfiguration;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.queueclient.message.Imports;
import com.walmartlabs.concord.server.queueclient.message.MessageType;
import com.walmartlabs.concord.server.queueclient.message.ProcessRequest;
import com.walmartlabs.concord.server.queueclient.message.ProcessResponse;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.websocket.WebSocketChannel;
import com.walmartlabs.concord.server.websocket.WebSocketChannelManager;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;
import static org.jooq.impl.DSL.*;

/**
 * Dispatches processes to agents.
 */
@Named
@Singleton
public class Dispatcher extends PeriodicTask {

    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);

    private static final long ERROR_DELAY = TimeUnit.SECONDS.toMillis(30);
    private static final long LOCK_KEY = 1552468327245L;

    private final Locks locks;
    private final DispatcherDao dao;
    private final WebSocketChannelManager channelManager;
    private final LogManager logManager;
    private final ProcessQueueManager queueManager;
    private final Set<Filter> filters;
    private final int batchSize;

    private final Histogram uniqueProjectsHistogram;
    private final Histogram dispatchedCountHistogram;

    @Inject
    public Dispatcher(Locks locks,
                      DispatcherDao dao,
                      WebSocketChannelManager channelManager,
                      LogManager logManager,
                      ProcessQueueManager queueManager,
                      Set<Filter> filters,
                      ProcessQueueConfiguration cfg,
                      MetricRegistry metricRegistry) {

        super(cfg.getDispatcherPollDelay(), ERROR_DELAY);

        this.locks = locks;
        this.dao = dao;
        this.channelManager = channelManager;
        this.logManager = logManager;
        this.queueManager = queueManager;
        this.filters = filters;

        this.batchSize = cfg.getDispatcherBatchSize();

        this.uniqueProjectsHistogram = metricRegistry.histogram("process-queue-dispatcher-unique-projects");
        this.dispatchedCountHistogram = metricRegistry.histogram("process-queue-dispatcher-dispatched-count");
    }

    @Override
    protected boolean performTask() {
        // TODO the WebSocketChannelManager business can be replaced with an async jax-rs endpoint and an "inbox" queue

        // grab the requests w/o responses
        Map<WebSocketChannel, ProcessRequest> requests = this.channelManager.getRequests(MessageType.PROCESS_REQUEST);
        if (requests.isEmpty()) {
            return false;
        }

        List<Request> l = requests.entrySet().stream()
                .map(e -> new Request(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        // run everything in a single transaction
        // take a global lock to avoid races
        return dao.txResult(tx -> {
            locks.lock(tx, LOCK_KEY);
            return dispatch(tx, l);
        });
    }

    private boolean dispatch(DSLContext tx, List<Request> requests) {
        // we need it modifiable
        List<Request> inbox = new ArrayList<>(requests);

        int offset = 0;
        Set<UUID> projectsToSkip = new HashSet<>();

        while (true) {
            // fetch the next few ENQUEUED processes from the DB
            List<ProcessQueueEntry> candidates = new ArrayList<>(dao.next(tx, offset, batchSize));
            if (candidates.isEmpty() || inbox.isEmpty()) {
                // no potential candidates or no requests left to process
                return false;
            }

            uniqueProjectsHistogram.update(countUniqueProjects(candidates));

            // filter out the candidates that shouldn't be dispatched at the moment
            for (Iterator<ProcessQueueEntry> it = candidates.iterator(); it.hasNext(); ) {
                ProcessQueueEntry e = it.next();

                // currently there are no filters applicable to standalone (i.e. without a project) processes
                if (e.projectId() == null) {
                    continue;
                }

                // see below
                if (projectsToSkip.contains(e.projectId())) {
                    it.remove();
                    continue;
                }

                if (!pass(tx, e)) {
                    // the candidate didn't pass the filter or can't lock the queue
                    // skip to the next candidate (of a different project)
                    it.remove();
                }

                // only one process per project can be dispatched at the time, currently the filters are not
                // designed to run multiple times per dispatch "tick"

                // TODO
                // this can be improved if filters accepted a list of candidates and returned a list of
                // those who passed. However, statistically each batch of candidates contains unique project IDs
                // so "multi-entry" filters are less effective and just complicate things
                // in the future we might need to consider batching up candidates by project IDs and using sharded
                // locks
                projectsToSkip.add(e.projectId());
            }

            List<Match> matches = match(inbox, candidates);
            if (matches.isEmpty()) {
                // no matches, try fetching the next N records
                offset += batchSize;
                continue;
            }

            for (Match m : matches) {
                ProcessQueueEntry candidate = m.response;

                // mark the process as STARTING and send it to the agent
                queueManager.updateStatus(tx, candidate.key(), ProcessStatus.STARTING);

                sendResponse(m);

                inbox.remove(m.request);
            }

            dispatchedCountHistogram.update(matches.size());
            return true;
        }
    }

    private List<Match> match(List<Request> requests, List<ProcessQueueEntry> candidates) {
        List<Match> results = new ArrayList<>();

        for (Request req : requests) {
            ProcessQueueEntry candidate = findCandidate(req.request, candidates);
            if (candidate == null) {
                continue;
            }

            // the process can be matched only once, remove the match from the list of candidates
            candidates.remove(candidate);

            results.add(new Match(req, candidate));
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private ProcessQueueEntry findCandidate(ProcessRequest req, List<ProcessQueueEntry> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }

        Map<String, Object> capabilities = req.getCapabilities();

        for (ProcessQueueEntry c : candidates) {
            Map<String, Object> requirements = c.requirements();
            if (requirements == null) {
                requirements = Collections.emptyMap();
            }

            Map<String, Object> m = (Map<String, Object>) requirements.getOrDefault("agent", Collections.emptyMap());
            if (MapMatcher.matches(capabilities, m)) {
                return c;
            }
        }

        return null;
    }

    private boolean pass(DSLContext tx, ProcessQueueEntry e) {
        for (Filter f : filters) {
            if (!f.apply(tx, e)) {
                return false;
            }
        }

        return true;
    }

    private void sendResponse(Match match) {
        WebSocketChannel channel = match.request.channel;
        long correlationId = match.request.request.getCorrelationId();

        ProcessQueueEntry item = match.response;

        SecretReference secret = null;
        if (item.repoId() != null) {
            secret = dao.getSecretReference(item.repoId());
        }

        ProcessResponse resp = new ProcessResponse(correlationId,
                item.key().getInstanceId(),
                secret != null ? secret.orgName : null,
                item.repoUrl(),
                item.repoPath(),
                item.commitId(),
                secret != null ? secret.secretName : null,
                item.imports());

        if (!channelManager.sendResponse(channel.getChannelId(), resp)) {
            log.warn("sendResponse ['{}'] -> failed", correlationId);
        }

        logManager.info(item.key(), "Acquired by: " + channel.getInfo());
    }

    private static int countUniqueProjects(List<ProcessQueueEntry> candidates) {
        if (candidates.isEmpty()) {
            return 0;
        }

        return candidates.stream()
                .map(ProcessQueueEntry::projectId)
                .collect(Collectors.toSet())
                .size();
    }

    @Named
    public static class DispatcherDao extends AbstractDao {

        private final ConcordObjectMapper objectMapper;
        private final Histogram offsetHistogram;

        @Inject
        public DispatcherDao(@MainDB Configuration cfg,
                             ConcordObjectMapper objectMapper,
                             MetricRegistry metricRegistry) {

            super(cfg);
            this.objectMapper = objectMapper;
            this.offsetHistogram = metricRegistry.histogram("process-queue-dispatcher-offset");
        }

        @Override
        protected <T> T txResult(TxResult<T> t) {
            return super.txResult(t);
        }

        @WithTimer
        public List<ProcessQueueEntry> next(DSLContext tx, int offset, int limit) {
            offsetHistogram.update(offset);

            ProcessQueue q = PROCESS_QUEUE.as("q");

            Field<UUID> orgIdField = select(PROJECTS.ORG_ID).from(PROJECTS).where(PROJECTS.PROJECT_ID.eq(q.PROJECT_ID)).asField();

            SelectJoinStep<Record13<UUID, Timestamp, UUID, UUID, UUID, UUID, String, String, String, UUID, JSONB, JSONB, JSONB>> s =
                    tx.select(
                            q.INSTANCE_ID,
                            q.CREATED_AT,
                            q.PROJECT_ID,
                            orgIdField,
                            q.INITIATOR_ID,
                            q.PARENT_INSTANCE_ID,
                            q.REPO_PATH,
                            q.REPO_URL,
                            q.COMMIT_ID,
                            q.REPO_ID,
                            q.IMPORTS,
                            q.REQUIREMENTS,
                            q.EXCLUSIVE)
                            .from(q);

            s.where(q.CURRENT_STATUS.eq(ProcessStatus.ENQUEUED.toString())
                    .and(or(q.START_AT.isNull(),
                            q.START_AT.le(currentTimestamp())))
                    .and(q.WAIT_CONDITIONS.isNull()));

            return s.orderBy(q.LAST_UPDATED_AT)
                    .offset(offset)
                    .limit(limit)
                    .forUpdate()
                    .of(q)
                    .skipLocked()
                    .fetch(r -> ProcessQueueEntry.builder()
                            .key(new ProcessKey(r.value1(), r.value2()))
                            .projectId(r.value3())
                            .orgId(r.value4())
                            .initiatorId(r.value5())
                            .parentInstanceId(r.value6())
                            .repoPath(r.value7())
                            .repoUrl(r.value8())
                            .commitId(r.value9())
                            .repoId(r.value10())
                            .imports(objectMapper.fromJSONB(r.value11(), Imports.class))
                            .requirements(objectMapper.fromJSONB(r.value12()))
                            .exclusive(objectMapper.fromJSONB(r.value13()))
                            .build());
        }

        public SecretReference getSecretReference(UUID repoId) {
            try (DSLContext tx = DSL.using(cfg)) {
                return tx.select(ORGANIZATIONS.ORG_NAME, SECRETS.SECRET_NAME)
                        .from(REPOSITORIES)
                        .leftOuterJoin(SECRETS).on(REPOSITORIES.SECRET_ID.eq(SECRETS.SECRET_ID))
                        .leftOuterJoin(ORGANIZATIONS).on(SECRETS.ORG_ID.eq(ORGANIZATIONS.ORG_ID))
                        .where(REPOSITORIES.REPO_ID.eq(repoId))
                        .fetchOne(r -> new SecretReference(r.value1(), r.value2()));
            }
        }
    }

    private static final class Request {

        private final WebSocketChannel channel;
        private final ProcessRequest request;

        private Request(WebSocketChannel channel, ProcessRequest request) {
            this.channel = channel;
            this.request = request;
        }
    }

    private static final class Match {

        private final Request request;
        private final ProcessQueueEntry response;

        private Match(Request request, ProcessQueueEntry response) {
            this.request = request;
            this.response = response;
        }
    }

    private static final class SecretReference {

        private final String orgName;
        private final String secretName;

        private SecretReference(String orgName, String secretName) {
            this.orgName = orgName;
            this.secretName = secretName;
        }
    }
}
