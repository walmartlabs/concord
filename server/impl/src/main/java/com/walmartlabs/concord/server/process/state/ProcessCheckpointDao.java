package com.walmartlabs.concord.server.process.state;

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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.process.ImmutableProcessCheckpointEntry;
import com.walmartlabs.concord.server.process.ProcessEntry.ProcessCheckpointEntry;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import org.jooq.Configuration;
import org.jooq.Record;

import javax.inject.Inject;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ProcessCheckpoints.PROCESS_CHECKPOINTS;

public class ProcessCheckpointDao extends AbstractDao {

    @Inject
    public ProcessCheckpointDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    public List<ProcessCheckpointEntry> list(ProcessKey processKey) {
        return txResult(tx -> tx.select()
                .from(PROCESS_CHECKPOINTS)
                .where(PROCESS_CHECKPOINTS.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_CHECKPOINTS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())))
                .fetch(ProcessCheckpointDao::toEntry));
    }

    public UUID getRecentId(ProcessKey processKey, String checkpointName) {
        return txResult(tx -> tx.select(PROCESS_CHECKPOINTS.CHECKPOINT_ID)
                .from(PROCESS_CHECKPOINTS)
                .where(PROCESS_CHECKPOINTS.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_CHECKPOINTS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                        .and(PROCESS_CHECKPOINTS.CHECKPOINT_NAME.eq(checkpointName)))
                .orderBy(PROCESS_CHECKPOINTS.CHECKPOINT_DATE.desc())
                .limit(1)
                .fetchOne(PROCESS_CHECKPOINTS.CHECKPOINT_ID));
    }

    public void importCheckpoint(ProcessKey processKey, UUID checkpointId, UUID correlationId, String checkpointName, Path data) {
        if (checkpointName.length() > PROCESS_CHECKPOINTS.CHECKPOINT_NAME.getDataType().length()) {
            throw new ValidationErrorsException("Invalid checkpoint name: value too long. Actual: " + checkpointName.length() + ", max: " + PROCESS_CHECKPOINTS.CHECKPOINT_NAME.getDataType().length());
        }
        tx(tx -> {
            String sql = tx.insertInto(PROCESS_CHECKPOINTS)
                    .columns(PROCESS_CHECKPOINTS.INSTANCE_ID,
                            PROCESS_CHECKPOINTS.INSTANCE_CREATED_AT,
                            PROCESS_CHECKPOINTS.CHECKPOINT_ID,
                            PROCESS_CHECKPOINTS.CHECKPOINT_NAME,
                            PROCESS_CHECKPOINTS.CHECKPOINT_DATE,
                            PROCESS_CHECKPOINTS.CHECKPOINT_DATA,
                            PROCESS_CHECKPOINTS.CORRELATION_ID)
                    .values((UUID) null, null, null, null, null, null, null)
                    .getSQL();

            tx.connection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, processKey.getInstanceId());
                    ps.setObject(2, processKey.getCreatedAt());
                    ps.setObject(3, checkpointId);
                    ps.setString(4, checkpointName);
                    ps.setTimestamp(5, new Timestamp(new Date().getTime()));
                    try (InputStream in = Files.newInputStream(data)) {
                        ps.setBinaryStream(6, in);
                    }
                    ps.setObject(7, correlationId);

                    ps.execute();
                }
            });
        });
    }

    public String export(ProcessKey processKey, UUID checkpointId, Path dest) {
        return txResult(tx -> {
            String sql = tx.select(PROCESS_CHECKPOINTS.CHECKPOINT_DATA, PROCESS_CHECKPOINTS.CHECKPOINT_NAME)
                    .from(PROCESS_CHECKPOINTS)
                    .where(PROCESS_CHECKPOINTS.CHECKPOINT_ID.eq(checkpointId)
                            .and(PROCESS_CHECKPOINTS.INSTANCE_ID.eq(processKey.getInstanceId())
                                    .and(PROCESS_CHECKPOINTS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))))
                    .getSQL();

            return tx.connectionResult(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, checkpointId);
                    ps.setObject(2, processKey.getInstanceId());
                    ps.setObject(3, processKey.getCreatedAt());

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return null;
                        }

                        try (InputStream in = rs.getBinaryStream(1)) {
                            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                        }
                        return rs.getString(2);
                    }
                }
            });
        });
    }

    private static ProcessCheckpointEntry toEntry(Record r) {
        return ImmutableProcessCheckpointEntry.builder()
                .id(r.get(PROCESS_CHECKPOINTS.CHECKPOINT_ID))
                .name(r.get(PROCESS_CHECKPOINTS.CHECKPOINT_NAME))
                .createdAt(r.get(PROCESS_CHECKPOINTS.CHECKPOINT_DATE))
                .correlationId(r.get(PROCESS_CHECKPOINTS.CORRELATION_ID))
                .build();
    }
}
