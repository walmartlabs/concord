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
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.agent.AgentCommand.Status;
import org.jooq.BatchBindStep;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.AgentCommands.AGENT_COMMANDS;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.value;

public class AgentCommandsDao extends AbstractDao {

    private final ObjectMapper objectMapper;

    @Inject
    public AgentCommandsDao(@MainDB Configuration cfg) {
        super(cfg);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void tx(Tx t) {
        super.tx(t);
    }

    public void insert(UUID commandId, String agentId, Map<String, Object> data) {
        tx(tx -> insert(tx, commandId, agentId, data));
    }

    public void insert(DSLContext tx, UUID commandId, String agentId, Map<String, Object> data) {
        tx.insertInto(AGENT_COMMANDS)
                .columns(AGENT_COMMANDS.COMMAND_ID, AGENT_COMMANDS.AGENT_ID,
                        AGENT_COMMANDS.COMMAND_STATUS, AGENT_COMMANDS.CREATED_AT,
                        AGENT_COMMANDS.COMMAND_DATA)
                .values(value(commandId), value(agentId),
                        value(Status.CREATED.toString()), currentOffsetDateTime(),
                        value(convert(data)))
                .execute();
    }

    public void insertBatch(List<AgentCommand> ace) {
        if (ace.isEmpty()) {
            return;
        }

        tx(tx -> {
            BatchBindStep q = tx.batch(tx.insertInto(AGENT_COMMANDS, AGENT_COMMANDS.COMMAND_ID, AGENT_COMMANDS.AGENT_ID,
                    AGENT_COMMANDS.COMMAND_STATUS, AGENT_COMMANDS.CREATED_AT,
                    AGENT_COMMANDS.COMMAND_DATA).values((UUID) null, null, null, null, null));

            for (AgentCommand ac : ace) {
                q.bind(value(ac.getCommandId()), value(ac.getAgentId()),
                        value(ac.getStatus().toString()), new Timestamp(System.currentTimeMillis()),
                        value(convert(ac.getData())));
            }

            q.execute();
        });
    }

    private byte[] convert(Map<String, Object> m) {
        try {
            return objectMapper.writeValueAsBytes(m);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
