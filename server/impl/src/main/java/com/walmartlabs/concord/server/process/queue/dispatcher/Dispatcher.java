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
import com.codahale.metrics.Timer;
import com.walmartlabs.concord.common.Matcher;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.runtime.v2.model.ExclusiveMode;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.Locks;
import com.walmartlabs.concord.server.PeriodicTask;
import com.walmartlabs.concord.server.cfg.ProcessQueueConfiguration;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.process.ImportsNormalizerFactory;
import com.walmartlabs.concord.server.process.SessionTokenCreator;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.queueclient.message.MessageType;
import com.walmartlabs.concord.server.queueclient.message.ProcessRequest;
import com.walmartlabs.concord.server.queueclient.message.ProcessResponse;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.websocket.WebSocketChannel;
import com.walmartlabs.concord.server.websocket.WebSocketChannelManager;
import org.jooq.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;
import static com.walmartlabs.concord.server.metrics.MetricUtils.withTimer;
import static org.jooq.impl.DSL.*;

/**
 * Dispatches processes to agents.
 */
public class Dispatcher extends PeriodicTask {

    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);

    private static final long ERROR_DELAY = TimeUnit.SECONDS.toMillis(30);
    private static final long LOCK_KEY = 1552468327245L;

    private final Locks locks;
    private final DispatcherDao dao;
    private final WebSocketChannelManager channelManager;
    private final ProcessLogManager logManager;
    private final ProcessQueueManager queueManager;
    private final Set<Filter> filters;
    private final ImportsNormalizerFactory importsNormalizerFactory;

    private final int batchSize;

    private final Histogram dispatchedCountHistogram;
    private final Timer responseTimer;

    private final SessionTokenCreator sessionTokenCreator;

    @Inject
    public Dispatcher(Locks locks,
                      DispatcherDao dao,
                      WebSocketChannelManager channelManager,
                      ProcessLogManager logManager,
                      ProcessQueueManager queueManager,
                      Set<Filter> filters,
                      ImportsNormalizerFactory importsNormalizerFactory,
                      ProcessQueueConfiguration cfg,
                      MetricRegistry metricRegistry,
                      SessionTokenCreator sessionTokenCreator) {

        super(cfg.getDispatcherPollDelay().toMillis(), ERROR_DELAY);

        this.locks = locks;
        this.dao = dao;
        this.channelManager = channelManager;
        this.logManager = logManager;
        this.queueManager = queueManager;
        this.filters = filters;
        this.importsNormalizerFactory = importsNormalizerFactory;

        this.batchSize = cfg.getDispatcherBatchSize();
        this.sessionTokenCreator = sessionTokenCreator;

        this.dispatchedCountHistogram = metricRegistry.histogram("process-queue-dispatcher-dispatched-count");
        this.responseTimer = metricRegistry.timer("process-queue-dispatcher-response-timer");
    }

    @Override
    @WithTimer
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

        // prepare all responses in a single transaction
        // take a global lock to avoid races
        List<Match> matches = dao.txResult(tx -> {
            locks.lock(tx, LOCK_KEY);
            try {
                return match(tx, l);
            } finally {
                filters.forEach(Filter::cleanup);
            }
        });

        dispatchedCountHistogram.update(matches.size());

        // no matches, retry after a delay
        if (matches.isEmpty()) {
            return false;
        }

        // send all responses in parallel
        withTimer(responseTimer, () -> matches.stream()
                .parallel()
                .forEach(this::sendResponse));

        return true;
    }

    private List<Match> match(DSLContext tx, List<Request> requests) {
        // we need it modifiable
        List<Request> inbox = new ArrayList<>(requests);

        int offset = 0;
        List<Match> matches = new ArrayList<>();
        while (true) {
            // fetch the next few ENQUEUED processes from the DB
            List<ProcessQueueEntry> candidates = dao.next(tx, offset, batchSize);
            if (candidates.isEmpty()) {
                break;
            }

            // filter out the candidates that shouldn't be dispatched at the moment (e.g. due to concurrency limits)
            for (ProcessQueueEntry e : candidates) {
                // find request/agent who can handle process
                Request req = findRequest(e, inbox);
                if (req == null) {
                    continue;
                }

                // "startingProcesses" are the currently collected "matches"
                // we keep them in a separate collection to simplify the filtering
                List<ProcessQueueEntry> startingProcesses = matches.stream().map(m -> m.response).collect(Collectors.toList());

                if (pass(tx, e, startingProcesses)) {
                    matches.add(new Match(req, e));
                    inbox.remove(req);

                    if (inbox.isEmpty()) {
                        break;
                    }
                }
            }

            if (inbox.isEmpty()) {
                break;
            }

            offset += batchSize;
        }

        for (Match m : matches) {
            ProcessQueueEntry candidate = m.response;

            // mark the process as STARTING
            queueManager.updateAgentId(tx, candidate.key(), m.request.channel.getAgentId(), ProcessStatus.STARTING);
        }

        return matches;
    }

    private static Request findRequest(ProcessQueueEntry candidate, List<Request> requests) {
        for (Request req : requests) {
            Map<String, Object> capabilities = req.request.getCapabilities();
            Map<String, Object> m = getAgentRequirements(candidate);
            if (m.isEmpty() || Matcher.matches(capabilities, m)) {
                return req;
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getAgentRequirements(ProcessQueueEntry entry) {
        Map<String, Object> requirements = entry.requirements();
        if (requirements == null) {
            return Collections.emptyMap();
        }

        Object agent = requirements.get("agent");
        if (agent instanceof Map) {
            return (Map<String, Object>) agent;
        }

        return Collections.emptyMap();
    }

    private boolean pass(DSLContext tx, ProcessQueueEntry e, List<ProcessQueueEntry> startingProcesses) {
        for (Filter f : filters) {
            if (!f.apply(tx, e, startingProcesses)) {
                return false;
            }
        }

        return true;
    }

    private void sendResponse(Match match) {
        WebSocketChannel channel = match.request.channel;
        long correlationId = match.request.request.getCorrelationId();
        ProcessQueueEntry item = match.response;

        try {
            SecretReference secret = null;
            if (item.repoId() != null) {
                secret = dao.getSecretReference(item.repoId());
            }

            // backward compatibility with old process queue entries that are not normalized
            Imports imports = importsNormalizerFactory.forProject(item.projectId())
                    .normalize(item.imports());

            ProcessResponse resp = new ProcessResponse(correlationId,
                    sessionTokenCreator.create(item.key()),
                    item.key().getInstanceId(),
                    secret != null ? secret.orgName : null,
                    item.repoUrl(),
                    item.repoPath(),
                    item.commitId(),
                    item.commitBranch(),
                    secret != null ? secret.secretName : null,
                    imports);

            if (!channelManager.sendResponse(channel.getChannelId(), resp)) {
                log.warn("sendResponse ['{}'] -> failed", correlationId);
            }

            logManager.info(item.key(), "Acquired by: " + channel.getUserAgent());
        } catch (Exception e) {
            log.error("sendResponse ['{}'] -> failed (instanceId: {})", correlationId, item.key().getInstanceId());
        }
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

            SelectJoinStep<Record14<UUID, OffsetDateTime, UUID, UUID, UUID, UUID, String, String, String, UUID, JSONB, JSONB, JSONB, String>> s =
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
                            q.EXCLUSIVE,
                            q.COMMIT_BRANCH)
                            .from(q);

            s.where(q.CURRENT_STATUS.eq(ProcessStatus.ENQUEUED.toString())
                    .and(or(q.START_AT.isNull(),
                            q.START_AT.le(currentOffsetDateTime()))));

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
                            .commitBranch(r.value14())
                            .repoId(r.value10())
                            .imports(objectMapper.fromJSONB(r.value11(), Imports.class))
                            .requirements(objectMapper.fromJSONB(r.value12()))
                            .exclusive(objectMapper.fromJSONB(r.value13(), ExclusiveMode.class))
                            .build());
        }

        public SecretReference getSecretReference(UUID repoId) {
            return dsl().select(ORGANIZATIONS.ORG_NAME, SECRETS.SECRET_NAME)
                    .from(REPOSITORIES)
                    .leftOuterJoin(SECRETS).on(REPOSITORIES.SECRET_ID.eq(SECRETS.SECRET_ID))
                    .leftOuterJoin(ORGANIZATIONS).on(SECRETS.ORG_ID.eq(ORGANIZATIONS.ORG_ID))
                    .where(REPOSITORIES.REPO_ID.eq(repoId))
                    .fetchOne(r -> new SecretReference(r.value1(), r.value2()));
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
