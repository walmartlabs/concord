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
import org.jooq.Configuration;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ProcessCheckpoints.PROCESS_CHECKPOINTS;

@Named
public class CheckpointDao extends AbstractDao {

    @Inject
    public CheckpointDao(@Named("app") Configuration cfg) {
        super(cfg);
    }

    public void importCheckpoint(UUID instanceId, UUID checkpointId, Path data) {
        tx(tx -> {
            String sql = tx.insertInto(PROCESS_CHECKPOINTS)
                    .columns(PROCESS_CHECKPOINTS.INSTANCE_ID, PROCESS_CHECKPOINTS.CHECKPOINT_ID, PROCESS_CHECKPOINTS.CHECKPOINT_DATA)
                    .values((UUID) null, null, null)
                    .getSQL();

            tx.connection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, instanceId);
                    ps.setObject(2, checkpointId);
                    try (InputStream in = Files.newInputStream(data)) {
                        ps.setBinaryStream(3, in);
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
}
