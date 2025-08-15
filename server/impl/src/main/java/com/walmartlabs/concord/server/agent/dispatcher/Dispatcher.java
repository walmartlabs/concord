package com.walmartlabs.concord.server.agent.dispatcher;

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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.CommandType;
import com.walmartlabs.concord.server.PeriodicTask;
import com.walmartlabs.concord.server.agent.AgentCommand;
import com.walmartlabs.concord.server.agent.Commands;
import com.walmartlabs.concord.server.message.MessageChannel;
import com.walmartlabs.concord.server.message.MessageChannelManager;
import com.walmartlabs.concord.server.cfg.AgentConfiguration;
import com.walmartlabs.concord.server.jooq.tables.records.AgentCommandsRecord;
import com.walmartlabs.concord.server.queueclient.message.CommandRequest;
import com.walmartlabs.concord.server.queueclient.message.CommandResponse;
import com.walmartlabs.concord.server.queueclient.message.MessageType;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.tables.AgentCommands.AGENT_COMMANDS;

/**
 * Dispatches commands to agents.
 */
public class Dispatcher extends PeriodicTask {

    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);

    private static final long ERROR_DELAY = 1 * 60 * 1000L; // 1 min
    private static final int BATCH_SIZE = 10;

    private final DispatcherDao dao;
    private final MessageChannelManager channelManager;

    @Inject
    public Dispatcher(AgentConfiguration cfg,
                      DispatcherDao dao,
                      MessageChannelManager channelManager) {

        super(cfg.getCommandPollDelay().toMillis(), ERROR_DELAY);
        this.dao = dao;
        this.channelManager = channelManager;
    }

    @Override
    protected boolean performTask() {
        Map<MessageChannel, CommandRequest> requests = this.channelManager.getRequests(MessageType.COMMAND_REQUEST);
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
            int offset = 0;

            while (true) {
                // fetch the next few CREATED commands from the DB
                List<AgentCommand> candidates = new ArrayList<>(dao.next(tx, offset, BATCH_SIZE));
                if (candidates.isEmpty() || inbox.isEmpty()) {
                    // no potential candidates or no requests left to process
                    break;
                }

                List<Match> matches = match(inbox, candidates);
                if (matches.isEmpty()) {
                    // no matches, try fetching the next N records
                    offset += BATCH_SIZE;
                    continue;
                }

                for (Match m : matches) {
                    sendResponse(m, m.command);
                    dao.markAsSent(tx, m.command.getCommandId());

                    inbox.remove(m.request);
                }
            }
        });
    }

    private List<Match> match(List<Request> requests, List<AgentCommand> candidates) {
        List<Match> results = new ArrayList<>();

        for (Request req : requests) {
            AgentCommand candidate = findCandidate(req.request, candidates);
            if (candidate == null) {
                continue;
            }

            // remove the matche from the list
            candidates.remove(candidate);

            results.add(new Match(req, candidate));
        }

        return results;
    }

    private AgentCommand findCandidate(CommandRequest req, List<AgentCommand> candidates) {
        return candidates.stream()
                .filter(c -> c.getAgentId().equals(req.getAgentId().toString()))
                .findFirst()
                .orElse(null);
    }

    private void sendResponse(Match match, AgentCommand response) {
        long correlationId = match.request.request.getCorrelationId();

        CommandType type = CommandType.valueOf((String) response.getData().remove(Commands.TYPE_KEY));

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type.toString());
        payload.putAll(response.getData());

        MessageChannel channel = match.request.channel;
        boolean success = channelManager.sendMessage(channel.getChannelId(), CommandResponse.cancel(correlationId, payload));
        if (success) {
            log.info("sendResponse ['{}'] -> done", correlationId);
        } else {
            log.error("sendResponse ['{}'] -> failed", correlationId);
        }
    }

    public static class DispatcherDao extends AbstractDao {

        private final ObjectMapper objectMapper;
        private final Histogram offsetHistogram;

        @Inject
        public DispatcherDao(@MainDB Configuration cfg,
                             MetricRegistry metricRegistry) {

            super(cfg);
            this.objectMapper = new ObjectMapper();
            this.offsetHistogram = metricRegistry.histogram("agent-command-dispatcher-offset");
        }

        @Override
        protected void tx(Tx t) {
            super.tx(t);
        }

        @WithTimer
        public List<AgentCommand> next(DSLContext tx, int offset, int limit) {
            offsetHistogram.update(offset);

            return tx.selectFrom(AGENT_COMMANDS)
                    .where(AGENT_COMMANDS.COMMAND_STATUS.eq(AgentCommand.Status.CREATED.toString()))
                    .orderBy(AGENT_COMMANDS.CREATED_AT)
                    .offset(offset)
                    .limit(limit)
                    .forUpdate()
                    .skipLocked()
                    .fetch(this::convert);
        }

        public void markAsSent(DSLContext tx, UUID commandId) {
            tx.update(AGENT_COMMANDS)
                    .set(AGENT_COMMANDS.COMMAND_STATUS, AgentCommand.Status.SENT.toString())
                    .where(AGENT_COMMANDS.COMMAND_ID.eq(commandId))
                    .execute();
        }

        @SuppressWarnings("unchecked")
        private AgentCommand convert(AgentCommandsRecord r) {
            UUID commandId = r.getCommandId();
            String agentId = r.getAgentId();
            OffsetDateTime createdAt = r.getCreatedAt();
            AgentCommand.Status status = AgentCommand.Status.valueOf(r.getCommandStatus());

            Map<String, Object> data;
            try {
                data = objectMapper.readValue(r.get(AGENT_COMMANDS.COMMAND_DATA), Map.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return new AgentCommand(commandId, agentId, status, createdAt, data);
        }
    }

    private record Match(Request request, AgentCommand command) {

    }

    private record Request(MessageChannel channel, CommandRequest request) {
    }
}
