package com.walmartlabs.concord.server.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.agent.AgentCommand.Status;
import com.walmartlabs.concord.server.jooq.tables.records.AgentCommandsRecord;
import org.jooq.Configuration;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    public Optional<AgentCommand> poll(String agentId) {
        Optional<AgentCommandsRecord> o = txResult(tx -> {
            AgentCommandsRecord r = tx.selectFrom(AGENT_COMMANDS)
                    .where(AGENT_COMMANDS.AGENT_ID.eq(agentId)
                            .and(AGENT_COMMANDS.COMMAND_STATUS.eq(Status.CREATED.toString())))
                    .orderBy(AGENT_COMMANDS.CREATED_AT)
                    .limit(1)
                    .forUpdate()
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
