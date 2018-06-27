package com.walmartlabs.concord.server.agent;

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


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.agent.AgentCommand.Status;
import com.walmartlabs.concord.server.jooq.tables.records.AgentCommandsRecord;
import org.jooq.BatchBindStep;
import org.jooq.Configuration;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static com.walmartlabs.concord.server.jooq.tables.AgentCommands.AGENT_COMMANDS;
import static org.jooq.impl.DSL.currentTimestamp;
import static org.jooq.impl.DSL.value;

@Named
public class AgentCommandsDao extends AbstractDao {

    private final ObjectMapper objectMapper;

    @Inject
    public AgentCommandsDao(Configuration cfg) {
        super(cfg);
        this.objectMapper = new ObjectMapper();
    }

    public void insert(UUID commandId, String agentId, Map<String, Object> data) {
        tx(tx -> tx.insertInto(AGENT_COMMANDS)
                .columns(AGENT_COMMANDS.COMMAND_ID, AGENT_COMMANDS.AGENT_ID,
                        AGENT_COMMANDS.COMMAND_STATUS, AGENT_COMMANDS.CREATED_AT,
                        AGENT_COMMANDS.COMMAND_DATA)
                .values(value(commandId), value(agentId),
                        value(Status.CREATED.toString()), currentTimestamp(),
                        value(convert(data)))
                .execute());
    }

    public void insertBatch(List<AgentCommand> ace) {
        tx(tx -> {
            BatchBindStep q = tx.batch(tx.insertInto(AGENT_COMMANDS, AGENT_COMMANDS.COMMAND_ID, AGENT_COMMANDS.AGENT_ID,
                    AGENT_COMMANDS.COMMAND_STATUS, AGENT_COMMANDS.CREATED_AT,
                    AGENT_COMMANDS.COMMAND_DATA).values((UUID) null, (String) null, (String) null, (Timestamp) null, (byte[]) null));

            for (AgentCommand ac : ace) {
                q.bind(value(ac.getCommandId()), value(ac.getAgentId()),
                        value(ac.getStatus().toString()), new Timestamp(System.currentTimeMillis()),
                        value(convert(ac.getData())));
            }

            q.execute();
        });
    }

    public Optional<AgentCommand> poll(String agentId) {
        Optional<AgentCommandsRecord> o = txResult(tx -> {
            AgentCommandsRecord r = tx.selectFrom(AGENT_COMMANDS)
                    .where(AGENT_COMMANDS.AGENT_ID.eq(agentId)
                            .and(AGENT_COMMANDS.COMMAND_STATUS.eq(Status.CREATED.toString())))
                    .orderBy(AGENT_COMMANDS.CREATED_AT)
                    .limit(1)
                    .forUpdate()
                    .skipLocked()
                    .fetchOne();

            if (r == null) {
                return Optional.empty();
            }

            tx.update(AGENT_COMMANDS)
                    .set(AGENT_COMMANDS.COMMAND_STATUS, Status.SENT.toString())
                    .where(AGENT_COMMANDS.COMMAND_ID.eq(r.getCommandId()))
                    .execute();

            return Optional.of(r);
        });

        return o.map(this::convert);
    }

    @SuppressWarnings("unchecked")
    private AgentCommand convert(AgentCommandsRecord r) {
        UUID commandId = r.getCommandId();
        String agentId = r.getAgentId();
        Date createdAt = r.getCreatedAt();
        Status status = Status.SENT;

        Map<String, Object> data;
        try {
            data = objectMapper.readValue(r.getCommandData(), Map.class);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        return new AgentCommand(commandId, agentId, status, createdAt, data);
    }

    private byte[] convert(Map<String, Object> m) {
        try {
            return objectMapper.writeValueAsBytes(m);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
