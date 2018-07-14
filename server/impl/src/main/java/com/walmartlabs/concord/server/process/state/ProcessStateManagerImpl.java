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

import com.walmartlabs.concord.common.Posix;
import com.walmartlabs.concord.db.AbstractDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.walmartlabs.concord.server.jooq.tables.ProcessState.PROCESS_STATE;

@Named
public class ProcessStateManagerImpl extends AbstractDao implements ProcessStateManager {

    @Inject
    protected ProcessStateManagerImpl(@Named("app") Configuration cfg) {
        super(cfg);
    }

    @Override
    public <T> Optional<T> get(UUID instanceId, String path, Function<InputStream, Optional<T>> converter) {
        try (DSLContext tx = DSL.using(cfg)) {
            return get(tx, instanceId, path, converter);
        }
    }

    private <T> Optional<T> get(DSLContext tx, UUID instanceId, String path, Function<InputStream, Optional<T>> converter) {
        String sql = tx.select(PROCESS_STATE.ITEM_DATA)
                .from(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq((UUID) null)
                        .and(PROCESS_STATE.ITEM_PATH.eq((String) null)))
                .getSQL();

        return tx.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, instanceId);
                ps.setString(2, path);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    try (InputStream in = rs.getBinaryStream(1)) {
                        return converter.apply(in);
                    }
                }
            }
        });
    }

    @Override
    public <T> List<T> forEach(UUID instanceId, String path, Function<InputStream, Optional<T>> converter) {
        try (DSLContext tx = DSL.using(cfg)) {
            String sql = tx.select(PROCESS_STATE.ITEM_DATA)
                    .from(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq((UUID) null)
                            .and(PROCESS_STATE.ITEM_PATH.startsWith((String) null)))
                    .getSQL();

            return tx.connectionResult(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, instanceId);
                    ps.setString(2, path);

                    List<T> result = new ArrayList<>();

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            try (InputStream in = rs.getBinaryStream(1)) {
                                Optional<T> o = converter.apply(in);
                                o.ifPresent(result::add);
                            }
                        }
                    }

                    return result;
                }
            });
        }
    }

    @Override
    public List<String> list(UUID instanceId, String path) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROCESS_STATE.ITEM_PATH)
                    .from(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_STATE.ITEM_PATH.startsWith(path)))
                    .fetch(PROCESS_STATE.ITEM_PATH);
        }
    }

    @Override
    public <T> Optional<T> findPath(UUID instanceId, String path, Function<Stream<String>, Optional<T>> converter) {
        try (DSLContext tx = DSL.using(cfg)) {
            Stream<String> s = tx.select(PROCESS_STATE.ITEM_PATH)
                    .from(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_STATE.ITEM_PATH.startsWith(path)))
                    .fetch(PROCESS_STATE.ITEM_PATH)
                    .stream();

            return converter.apply(s);
        }
    }

    @Override
    public boolean exists(UUID instanceId, String path) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.fetchExists(tx.selectFrom(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_STATE.ITEM_PATH.startsWith(path))));
        }
    }

    private void delete(DSLContext tx, UUID instanceId) {
        tx.deleteFrom(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId))
                .execute();
    }

    @Override
    public void delete(UUID instanceId, String path) {
        tx(tx -> delete(tx, instanceId, path));
    }

    private void delete(DSLContext tx, UUID instanceId, String path) {
        tx.deleteFrom(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId))
                .and(PROCESS_STATE.ITEM_PATH.startsWith(path))
                .execute();
    }

    @Override
    public void replace(UUID instanceId, String path, byte[] data) {
        tx(tx -> {
            delete(tx, instanceId, path);
            insert(tx, instanceId, path, data);
        });
    }

    @Override
    public void insert(UUID instanceId, String path, byte[] data) {
        tx(tx -> insert(tx, instanceId, path, data));
    }

    private void insert(DSLContext tx, UUID instanceId, String path, byte[] data) {
        tx.insertInto(PROCESS_STATE)
                .columns(PROCESS_STATE.INSTANCE_ID, PROCESS_STATE.ITEM_PATH, PROCESS_STATE.ITEM_DATA)
                .values(instanceId, path, data)
                .execute();
    }

    @Override
    public void importPath(UUID instanceId, String path, Path src) {
        tx(tx -> importPath(tx, instanceId, path, src, f -> true));
    }

    @Override
    public void replacePath(UUID instanceId, Path src, Function<Path, Boolean> filter) {
        tx(tx -> {
            delete(tx, instanceId);
            importPath(tx, instanceId, src, filter);
        });
    }

    private void importPath(DSLContext tx, UUID instanceId, Path src, Function<Path, Boolean> filter) {
        importPath(tx, instanceId, null, src, filter);
    }

    private void importPath(DSLContext tx, UUID instanceId, String path, Path src, Function<Path, Boolean> filter) {
        String prefix = fixPath(path);

        String sql = tx.insertInto(PROCESS_STATE)
                .columns(PROCESS_STATE.INSTANCE_ID, PROCESS_STATE.ITEM_PATH, PROCESS_STATE.UNIX_MODE, PROCESS_STATE.ITEM_DATA)
                .values((UUID) null, null, null, null)
                .onConflict(PROCESS_STATE.INSTANCE_ID, PROCESS_STATE.ITEM_PATH)
                .doUpdate().set(PROCESS_STATE.UNIX_MODE, (Short) null).set(PROCESS_STATE.ITEM_DATA, (byte[]) null)
                .getSQL();

        tx.connection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                    private long bytesInBatch = 0;

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path p = src.relativize(file);

                        if (!filter.apply(p)) {
                            return FileVisitResult.CONTINUE;
                        }

                        String n = p.toString();
                        if (prefix != null) {
                            n = prefix + n;
                        }

                        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
                        int unixMode = Posix.unixMode(permissions);

                        try {
                            ps.setObject(1, instanceId);
                            ps.setString(2, n);
                            ps.setInt(3, unixMode);
                            try (InputStream in = Files.newInputStream(file)) {
                                ps.setBinaryStream(4, in);
                            }
                            ps.setInt(5, unixMode);
                            try (InputStream in = Files.newInputStream(file)) {
                                ps.setBinaryStream(6, in);
                            }
                            ps.addBatch();

                            bytesInBatch += Files.size(file);
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }

                        // limit the size of the batch
                        if (bytesInBatch >= MAX_IMPORT_BATCH_SIZE_BYTES) {
                            try {
                                ps.executeBatch();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                            bytesInBatch = 0;
                        }

                        return FileVisitResult.CONTINUE;
                    }
                });

                ps.executeBatch();
            }
        });
    }

    @Override
    public boolean export(UUID instanceId, ItemConsumer consumer) {
        try (DSLContext tx = DSL.using(cfg)) {
            String sql = tx
                    .select(PROCESS_STATE.ITEM_PATH, PROCESS_STATE.UNIX_MODE, PROCESS_STATE.ITEM_DATA)
                    .from(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq((UUID) null))
                    .getSQL();

            return tx.connectionResult(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, instanceId);

                    boolean found = false;
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            found = true;

                            String n = rs.getString(1);
                            int unixMode = rs.getInt(2);
                            try (InputStream in = rs.getBinaryStream(3)) {
                                consumer.accept(n, unixMode, in);
                            }
                        }
                    }

                    return found;
                }
            });
        }
    }

    @Override
    public boolean exportDirectory(UUID instanceId, String path, ItemConsumer consumer) {
        String dir = fixPath(path);

        try (DSLContext tx = DSL.using(cfg)) {
            String sql = tx
                    .select(PROCESS_STATE.ITEM_PATH, PROCESS_STATE.UNIX_MODE, PROCESS_STATE.ITEM_DATA)
                    .from(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq((UUID) null)
                            .and(PROCESS_STATE.ITEM_PATH.startsWith((String) null)))
                    .getSQL();

            return tx.connectionResult(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, instanceId);
                    ps.setString(2, dir);

                    boolean found = false;
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            found = true;

                            String n = relativize(dir, rs.getString(1));
                            int unixMode = rs.getInt(2);
                            try (InputStream in = rs.getBinaryStream(3)) {
                                consumer.accept(n, unixMode, in);
                            }
                        }
                    }

                    return found;
                }
            });
        }
    }

    private static String fixPath(String p) {
        if (p == null) {
            return null;
        }

        if (!p.endsWith(PATH_SEPARATOR)) {
            p = p + PATH_SEPARATOR;
        }

        return p;
    }

    private static String relativize(String parent, String child) {
        int i = child.indexOf(parent);
        if (i < 0) {
            throw new IllegalArgumentException("Can't relativize '" + child + "' from '" + parent + "'");
        }
        return child.substring(parent.length());
    }
}
