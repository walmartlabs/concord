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

import com.walmartlabs.concord.common.MapMatcher;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.PeriodicTask;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueEntry;
import com.walmartlabs.concord.server.queueclient.message.Imports;
import com.walmartlabs.concord.server.queueclient.message.MessageType;
import com.walmartlabs.concord.server.queueclient.message.ProcessRequest;
import com.walmartlabs.concord.server.queueclient.message.ProcessResponse;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.websocket.WebSocketChannel;
import com.walmartlabs.concord.server.websocket.WebSocketChannelManager;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.CallableStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static org.jooq.impl.DSL.*;

/**
 * Dispatches processes to agents.
 */
@Named
@Singleton
public class Dispatcher extends PeriodicTask {

    private static final long POLL_DELAY = TimeUnit.SECONDS.toMillis(1);
    private static final long ERROR_DELAY = TimeUnit.SECONDS.toMillis(30);

    private final DispatcherDao dao;
    private final WebSocketChannelManager channelManager;
    private final LogManager logManager;
    private final OrganizationDao organizationDao;
    private final RepositoryDao repositoryDao;
    private final ProcessQueueDao queueDao;
    private final Set<Filter> filters;

    @Inject
    public Dispatcher(DispatcherDao dao,
                      WebSocketChannelManager channelManager,
                      LogManager logManager,
                      OrganizationDao organizationDao,
                      RepositoryDao repositoryDao,
                      ProcessQueueDao queueDao,
                      Set<Filter> filters) {

        super(POLL_DELAY, ERROR_DELAY);

        this.dao = dao;
        this.channelManager = channelManager;
        this.logManager = logManager;
        this.organizationDao = organizationDao;
        this.repositoryDao = repositoryDao;
        this.queueDao = queueDao;
        this.filters = filters;
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

        dispatch(l);

        return false;
    }

    private void dispatch(List<Request> requests) {
        // we need it modifiable
        List<Request> inbox = new ArrayList<>(requests);

        // run everything in a single transaction
        dao.tx(tx -> {
            // fetch the next few ENQUEUED processes from the DB
            List<ProcessQueueEntry> candidates = new ArrayList<>(dao.next(tx));
            if (candidates.isEmpty()) {
                return;
            }

            Set<UUID> projectsToSkip = new HashSet<>();

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

                // TODO sharded locks?
                boolean locked = dao.tryLock(tx);
                if (!locked || !pass(tx, e)) {
                    // the candidate didn't pass the filter or can't lock the queue
                    // skip to the next candidate (of a different project)
                    it.remove();
                }

                // only one process per project can be dispatched at the time, currently the filters are not
                // designed to run multiple times per dispatch "tick"
                // TODO this can be improved if filters accepted a list of candidates and returned a list of those who passed
                projectsToSkip.add(e.projectId());
            }

            while (true) {
                if (candidates.isEmpty() || inbox.isEmpty()) {
                    // no potential candidates or no requests left to process
                    break;
                }

                List<Match> matches = match(inbox, candidates);
                if (matches.isEmpty()) {
                    break;
                }

                for (Match m : matches) {
                    ProcessQueueEntry candidate = m.response;

                    // mark the process as STARTING and send it to the agent
                    // TODO ProcessQueueDao#updateStatus should be moved to ProcessManager because it does two things (updates the record and inserts a status history entry)
                    queueDao.updateStatus(tx, candidate.key(), ProcessStatus.STARTING);

                    sendResponse(m);

                    inbox.remove(m.request);
                }
            }
        });
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

        // TODO this should be fetched in DispatcherDao#next
        String orgName = null;
        String secret = null;
        if (item.repoId() != null) {
            RepositoryEntry repository = repositoryDao.get(item.repoId());
            if (repository != null) {
                secret = repository.getSecretName();
            }
        }
        if (item.orgId() != null) {
            orgName = organizationDao.get(item.orgId()).getName();
        }

        // TODO don't we need the secret's org as well?
        ProcessResponse resp = new ProcessResponse(correlationId,
                item.key().getInstanceId(),
                orgName,
                item.repoUrl(),
                item.repoPath(),
                item.commitId(),
                secret,
                item.imports());

        channelManager.sendResponse(channel.getChannelId(), resp);

        logManager.info(item.key(), "Acquired by: " + channel.getInfo());
    }

    @Named
    public static class DispatcherDao extends AbstractDao {

        private static final long LOCK_KEY = 1552468327245L;
        private static final int BATCH_SIZE = 10;

        private final ConcordObjectMapper objectMapper;

        @Inject
        public DispatcherDao(@MainDB Configuration cfg, ConcordObjectMapper objectMapper) {
            super(cfg);
            this.objectMapper = objectMapper;
        }

        @Override
        public void tx(Tx t) {
            super.tx(t);
        }

        @WithTimer
        public List<ProcessQueueEntry> next(DSLContext tx) {
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

            return s.orderBy(q.CREATED_AT)
                    .limit(BATCH_SIZE)
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

        public boolean tryLock(DSLContext tx) {
            String sql = "{ ? = call pg_try_advisory_xact_lock(?) }";

            return tx.connectionResult(conn -> {
                try (CallableStatement cs = conn.prepareCall(sql)) {
                    cs.registerOutParameter(1, Types.BOOLEAN);
                    cs.setLong(2, LOCK_KEY);
                    cs.execute();
                    return cs.getBoolean(1);
                }
            });
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
}
