package com.walmartlabs.concord.server.process.state;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.google.common.base.Throwables;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.Posix;
import com.walmartlabs.concord.db.AbstractDao;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.walmartlabs.concord.server.jooq.tables.ProcessState.PROCESS_STATE;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.value;

@Named
public class ProcessStateManager extends AbstractDao {

    private static final String PATH_SEPARATOR = "/";
    private static final long MAX_IMPORT_BATCH_SIZE_BYTES = 64 * 1024 * 1024;

    @Inject
    protected ProcessStateManager(Configuration cfg) {
        super(cfg);
    }

    /**
     * Executes multiple operations in a single transaction.
     *
     * @param tx
     */
    public void transaction(Tx tx) {
        tx(tx);
    }

    /**
     * Executes multiple operations in a single transaction and returns some value.
     *
     * @param tx
     * @param <T>
     * @return
     */
    public <T> T transactionResult(TxResult<T> tx) {
        return txResult(tx);
    }

    /**
     * Fetches a single value specified by its path and applies a converter function.
     */
    public <T> Optional<T> get(UUID instanceId, String path, Function<InputStream, Optional<T>> converter) {
        try (DSLContext tx = DSL.using(cfg)) {
            return get(tx, instanceId, path, converter);
        }
    }

    public <T> Optional<T> get(DSLContext tx, UUID instanceId, String path, Function<InputStream, Optional<T>> converter) {
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

    /**
     * Fetches multiple values whose path begins with the specified value and applies a converter function
     * to each value.
     */
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

    /**
     * Retrieves a list of resources whose path begins with the specified value.
     */
    public List<String> list(UUID instanceId, String path) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROCESS_STATE.ITEM_PATH)
                    .from(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_STATE.ITEM_PATH.startsWith(path)))
                    .fetch(PROCESS_STATE.ITEM_PATH);
        }
    }

    /**
     * Find all item paths that starts with the specified value.
     */
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

    /**
     * Check if there any data for the given process ID.
     */
    public boolean exists(UUID instanceId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.fetchExists(tx.selectFrom(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)));
        }
    }

    /**
     * Checks if a value exists.
     */
    public boolean exists(UUID instanceId, String path) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.fetchExists(tx.selectFrom(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_STATE.ITEM_PATH.startsWith(path))));
        }
    }

    /**
     * Removes all data of the specified process.
     *
     * @return {@code true} if values were found and removed.
     */
    public boolean delete(DSLContext tx, UUID instanceId) {
        int rows = tx.deleteFrom(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId))
                .execute();

        return rows > 0;
    }

    /**
     * Removes a single value.
     *
     * @returns {@code true} if a value was found and removed.
     */
    public boolean delete(UUID instanceId, String path) {
        return transactionResult(tx -> delete(tx, instanceId, path));
    }

    public boolean delete(DSLContext tx, UUID instanceId, String path) {
        int rows = tx.deleteFrom(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId))
                .and(PROCESS_STATE.ITEM_PATH.startsWith(path))
                .execute();

        return rows > 0;
    }

    public void insert(UUID instanceId, String path, byte[] data) {
        tx(tx -> insert(tx, instanceId, path, data));
    }

    public void insert(DSLContext tx, UUID instanceId, String path, byte[] data) {
        tx.insertInto(PROCESS_STATE)
                .columns(PROCESS_STATE.INSTANCE_ID, PROCESS_STATE.ITEM_PATH, PROCESS_STATE.ITEM_DATA)
                .values(instanceId, path, data)
                .execute();
    }

    /**
     * Imports data from the specified directory or a file.
     *
     * @param tx         current transaction
     * @param instanceId process instance ID
     * @param path       target path prefix
     * @param src        source directory or a file
     */
    public void importPath(DSLContext tx, UUID instanceId, String path, Path src) {
        importPath(tx, instanceId, path, src, f -> true);
    }

    /**
     * Imports data from the specified directory or a file. If the filter function
     * returns {@code false}, the matching file will be skipped.
     *
     * @param tx         current transaction
     * @param instanceId process instance ID
     * @param src        source directory or a file
     * @param filter     filter function
     */
    public void importPath(DSLContext tx, UUID instanceId, Path src, Function<Path, Boolean> filter) {
        importPath(tx, instanceId, null, src, filter);
    }

    /**
     * Imports data from the specified directory or a file. If the filter function
     * returns {@code false}, the matching file will be skipped.
     *
     * @param tx         current transaction
     * @param instanceId process instance ID
     * @param path       target path prefix
     * @param src        source directory or a file
     * @param filter     filter function
     */
    public void importPath(DSLContext tx, UUID instanceId, String path, Path src, Function<Path, Boolean> filter) {
        String prefix = fixPath(path);

        String sql = tx.insertInto(PROCESS_STATE)
                .columns(PROCESS_STATE.INSTANCE_ID, PROCESS_STATE.ITEM_PATH, PROCESS_STATE.UNIX_MODE, PROCESS_STATE.ITEM_DATA)
                .values((UUID) null, null, null, null)
                .onConflict(PROCESS_STATE.INSTANCE_ID, PROCESS_STATE.ITEM_PATH)
                .doUpdate().set(PROCESS_STATE.UNIX_MODE, (Short)null).set(PROCESS_STATE.ITEM_DATA, (byte[])null)
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
                            throw Throwables.propagate(e);
                        }

                        // limit the size of the batch
                        if (bytesInBatch >= MAX_IMPORT_BATCH_SIZE_BYTES) {
                            try {
                                ps.executeBatch();
                            } catch (Exception e) {
                                throw Throwables.propagate(e);
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

    /**
     * Exports all data of a process instance.
     *
     * @param instanceId process instance ID
     * @param consumer   a function that receives the name of a file and a data stream
     * @return {@code true} if at least a single element was exported.
     */
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

    /**
     * Exports elements whose path begins with the specified value.
     *
     * @param instanceId process instance ID
     * @param path       path prefix
     * @param consumer   a function that receives the name of a file and a data stream
     * @return {@code true} if at least a single element was exported.
     */
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

    public boolean copy(DSLContext tx, UUID src, UUID dst) {
        int i = tx.insertInto(PROCESS_STATE)
                .select(select(value(dst), PROCESS_STATE.ITEM_PATH, PROCESS_STATE.ITEM_DATA)
                        .from(PROCESS_STATE)
                        .where(PROCESS_STATE.INSTANCE_ID.eq(src)))
                .execute();
        return i > 0;
    }

    public void update(DSLContext tx, UUID instanceId, String path, byte[] data) {
        delete(tx, instanceId, path);
        insert(tx, instanceId, path, data);
    }

    private static String fixPath(String p) {
        if (p == null) {
            return p;
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

    /**
     * Copies the data to the specified target directory.
     *
     * @param dst     target directory
     * @param options optional copy options
     * @return
     */
    public static ItemConsumer copyTo(Path dst, OpenOption... options) {
        return new CopyConsumer(dst, null, options);
    }

    /**
     * Copies the data to the specified target directory. Skips the ignored files.
     *
     * @param dst     target directory
     * @param ignored name patterns or ignored files
     * @param options optional copy options
     * @return
     */
    public static ItemConsumer copyTo(Path dst, String[] ignored, OpenOption... options) {
        return new CopyConsumer(dst, ignored, options);
    }

    /**
     * Puts all elements into the specified ZIP archive stream.
     *
     * @param dst archive stream.
     * @return
     */
    public static ItemConsumer zipTo(ZipArchiveOutputStream dst) {
        return new ZipConsumer(dst);
    }

    /**
     * Creates a path from the specified array of elements.
     *
     * @param elements
     * @return
     */
    public static String path(String... elements) {
        return String.join(PATH_SEPARATOR, elements);
    }

    private static final class CopyConsumer implements ItemConsumer {

        private final Path dst;
        private final String[] ignored;
        private final OpenOption[] options;

        private CopyConsumer(Path dst, String[] ignored, OpenOption[] options) {
            this.dst = dst;
            this.ignored = ignored;
            this.options = options;
        }

        @Override
        public void accept(String name, int unixMode, InputStream src) {
            if (ignored != null) {
                for (String i : ignored) {
                    if (name.matches(i)) {
                        return;
                    }
                }
            }

            Path p = dst.resolve(name);

            try {
                Path parent = p.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                try (OutputStream dst = Files.newOutputStream(p, options)) {
                    IOUtils.copy(src, dst);
                }

                Files.setPosixFilePermissions(p, Posix.posix(unixMode));
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private static final class ZipConsumer implements ItemConsumer {

        private final ZipArchiveOutputStream dst;

        private ZipConsumer(ZipArchiveOutputStream dst) {
            this.dst = dst;
        }

        @Override
        public void accept(String name, int unixMode, InputStream src) {
            ZipArchiveEntry entry = new ZipArchiveEntry(name);
            entry.setUnixMode(unixMode);

            try {
                dst.putArchiveEntry(entry);
                IOUtils.copy(src, dst);
                dst.closeArchiveEntry();
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private interface ItemConsumer {

        void accept(String name, int unixMode, InputStream src);
    }
}
