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
import com.walmartlabs.concord.server.process.ImmutableProcessCheckpointEntry;
import com.walmartlabs.concord.server.process.PartialProcessKey;
import com.walmartlabs.concord.server.process.ProcessEntry.ProcessCheckpointEntry;
import org.jooq.Configuration;
import org.jooq.Record;

import javax.inject.Inject;
import javax.inject.Named;
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

@Named
public class CheckpointDao extends AbstractDao {

    @Inject
    public CheckpointDao(@Named("app") Configuration cfg) {
        super(cfg);
    }

    public List<ProcessCheckpointEntry> list(PartialProcessKey processKey) {
        UUID instanceId = processKey.getInstanceId();
        return txResult(tx -> tx.select()
                .from(PROCESS_CHECKPOINTS)
                .where(PROCESS_CHECKPOINTS.INSTANCE_ID.eq(instanceId))
                .fetch(CheckpointDao::toEntry));
    }

    public UUID getRecentId(UUID instanceId, String checkpointName) {
        return txResult(tx -> tx.select(PROCESS_CHECKPOINTS.CHECKPOINT_ID)
                .from(PROCESS_CHECKPOINTS)
                .where(PROCESS_CHECKPOINTS.INSTANCE_ID.eq(instanceId).and(PROCESS_CHECKPOINTS.CHECKPOINT_NAME.eq(checkpointName)))
                .orderBy(PROCESS_CHECKPOINTS.CHECKPOINT_DATE.desc())
                .limit(1)
                .fetchOne(PROCESS_CHECKPOINTS.CHECKPOINT_ID));
    }

    public void importCheckpoint(UUID instanceId, UUID checkpointId, String checkpointName, Path data) {
        tx(tx -> {
            String sql = tx.insertInto(PROCESS_CHECKPOINTS)
                    .columns(PROCESS_CHECKPOINTS.INSTANCE_ID,
                            PROCESS_CHECKPOINTS.CHECKPOINT_ID,
                            PROCESS_CHECKPOINTS.CHECKPOINT_NAME,
                            PROCESS_CHECKPOINTS.CHECKPOINT_DATE,
                            PROCESS_CHECKPOINTS.CHECKPOINT_DATA)
                    .values((UUID) null, null, null, null, null)
                    .getSQL();

            tx.connection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, instanceId);
                    ps.setObject(2, checkpointId);
                    ps.setObject(3, checkpointName);
                    ps.setObject(4, new Timestamp(new Date().getTime()));
                    try (InputStream in = Files.newInputStream(data)) {
                        ps.setBinaryStream(5, in);
                    }

                    ps.execute();
                }
            });
        });
    }

    public boolean export(UUID checkpointId, Path dest) {
        return txResult(tx -> {
            String sql = tx.select(PROCESS_CHECKPOINTS.CHECKPOINT_DATA)
                    .from(PROCESS_CHECKPOINTS)
                    .where(PROCESS_CHECKPOINTS.CHECKPOINT_ID.eq(checkpointId))
                    .getSQL();

            return tx.connectionResult(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, checkpointId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return false;
                        }

                        try (InputStream in = rs.getBinaryStream(1)) {
                            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    return true;
                }
            });
        });
    }

    public void delete(UUID checkpointId) {
        tx(tx ->
                tx.deleteFrom(PROCESS_CHECKPOINTS)
                        .where(PROCESS_CHECKPOINTS.CHECKPOINT_ID.eq(checkpointId))
                        .execute()
        );
    }

    private static ProcessCheckpointEntry toEntry(Record r) {
        return ImmutableProcessCheckpointEntry.builder()
                .id(r.get(PROCESS_CHECKPOINTS.CHECKPOINT_ID))
                .name(r.get(PROCESS_CHECKPOINTS.CHECKPOINT_NAME))
                .createdAt(r.get(PROCESS_CHECKPOINTS.CHECKPOINT_DATE))
                .build();
    }
}
