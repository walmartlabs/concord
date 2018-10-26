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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.Posix;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.cfg.ProcessStateConfiguration;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.secret.SecretUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.ProcessState.PROCESS_STATE;

@Named
@Singleton
public class ProcessStateManager extends AbstractDao {

    private static final String PATH_SEPARATOR = "/";
    private static final long MAX_IMPORT_BATCH_SIZE_BYTES = 64 * 1024 * 1024;

    private final SecretStoreConfiguration secretCfg;
    private final Set<String> secureFiles = new HashSet<>();

    @Inject
    protected ProcessStateManager(@Named("app") Configuration cfg,
                                  SecretStoreConfiguration secretCfg,
                                  ProcessStateConfiguration stateCfg) {
        super(cfg);
        this.secretCfg = secretCfg;

        this.secureFiles.addAll(stateCfg.getSecureFiles());
    }

    public <T> Optional<T> get(UUID instanceId, String path, Function<InputStream, Optional<T>> converter) {
        try (DSLContext tx = DSL.using(cfg)) {
            return get(tx, instanceId, assertCreatedAt(tx, instanceId), path, converter);
        }
    }

    /**
     * Fetches a single value specified by its path and applies a converter function.
     */
    public <T> Optional<T> get(UUID instanceId, Timestamp instanceCreatedAt, String path, Function<InputStream, Optional<T>> converter) {
        try (DSLContext tx = DSL.using(cfg)) {
            return get(tx, instanceId, instanceCreatedAt, path, converter);
        }
    }

    private <T> Optional<T> get(DSLContext tx, UUID instanceId, Timestamp instanceCreatedAt, String path, Function<InputStream, Optional<T>> converter) {
        String sql = tx.select(PROCESS_STATE.IS_ENCRYPTED, PROCESS_STATE.ITEM_DATA)
                .from(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq((UUID) null)
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq((Timestamp) null))
                        .and(PROCESS_STATE.ITEM_PATH.eq((String) null)))
                .getSQL();

        return tx.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, instanceId);
                ps.setTimestamp(2, instanceCreatedAt);
                ps.setString(3, path);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    boolean encrypted = rs.getBoolean(1);
                    try (InputStream in = rs.getBinaryStream(2);
                         InputStream processed = encrypted ? decrypt(in) : in) {
                        return converter.apply(processed);
                    }
                }
            }
        });
    }

    public <T> List<T> forEach(UUID instanceId, String path, Function<InputStream, Optional<T>> converter) {
        Timestamp instanceCreatedAt = assertCreatedAt(instanceId);
        return forEach(instanceId, instanceCreatedAt, path, converter);
    }

    /**
     * Fetches multiple values whose path begins with the specified value and applies a converter function
     * to each value.
     */
    public <T> List<T> forEach(UUID instanceId, Timestamp instanceCreatedAt, String path, Function<InputStream, Optional<T>> converter) {
        try (DSLContext tx = DSL.using(cfg)) {
            String sql = tx.select(PROCESS_STATE.IS_ENCRYPTED, PROCESS_STATE.ITEM_DATA)
                    .from(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq((UUID) null)
                            .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq((Timestamp) null))
                            .and(PROCESS_STATE.ITEM_PATH.startsWith((String) null)))
                    .getSQL();

            return tx.connectionResult(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, instanceId);
                    ps.setTimestamp(2, instanceCreatedAt);
                    ps.setString(3, path);

                    List<T> result = new ArrayList<>();

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            boolean encrypted = rs.getBoolean(1);
                            try (InputStream in = rs.getBinaryStream(2);
                                 InputStream processed = encrypted ? decrypt(in) : in) {
                                Optional<T> o = converter.apply(processed);
                                o.ifPresent(result::add);
                            }
                        }
                    }

                    return result;
                }
            });
        }
    }

    public List<String> list(UUID instanceId, String path) {
        Timestamp instanceCreatedAt = assertCreatedAt(instanceId);
        return list(instanceId, instanceCreatedAt, path);
    }

    /**
     * Retrieves a list of resources whose path begins with the specified value.
     */
    public List<String> list(UUID instanceId, Timestamp instanceCreatedAt, String path) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROCESS_STATE.ITEM_PATH)
                    .from(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(instanceCreatedAt))
                            .and(PROCESS_STATE.ITEM_PATH.startsWith(path)))
                    .fetch(PROCESS_STATE.ITEM_PATH);
        }
    }

    public <T> Optional<T> findPath(UUID instanceId, String path, Function<Stream<String>, Optional<T>> converter) {
        Timestamp instanceCreatedAt = assertCreatedAt(instanceId);
        return findPath(instanceId, instanceCreatedAt, path, converter);
    }

    /**
     * Finds all item paths that starts with the specified value.
     */
    public <T> Optional<T> findPath(UUID instanceId, Timestamp instanceCreatedAt, String path, Function<Stream<String>, Optional<T>> converter) {
        try (DSLContext tx = DSL.using(cfg)) {
            Stream<String> s = tx.select(PROCESS_STATE.ITEM_PATH)
                    .from(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(instanceCreatedAt))
                            .and(PROCESS_STATE.ITEM_PATH.startsWith(path)))
                    .fetch(PROCESS_STATE.ITEM_PATH)
                    .stream();

            return converter.apply(s);
        }
    }

    public boolean exists(UUID instanceId, String path) {
        Timestamp instanceCreatedAt = assertCreatedAt(instanceId);
        return exists(instanceId, instanceCreatedAt, path);
    }

    /**
     * Checks if a value exists.
     */
    public boolean exists(UUID instanceId, Timestamp instanceCreatedAt, String path) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.fetchExists(tx.selectFrom(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(instanceCreatedAt))
                            .and(PROCESS_STATE.ITEM_PATH.startsWith(path))));
        }
    }

    private void delete(DSLContext tx, UUID instanceId, Timestamp instanceCreatedAt) {
        tx.deleteFrom(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(instanceCreatedAt)))
                .execute();
    }

    public void deleteFile(UUID instanceId, String path) {
        Timestamp instanceCreatedAt = assertCreatedAt(instanceId);
        deleteFile(instanceId, instanceCreatedAt, path);
    }

    /**
     * Removes single value.
     */
    public void deleteFile(UUID instanceId, Timestamp instanceCreatedAt, String path) {
        tx(tx -> deleteFile(tx, instanceId, instanceCreatedAt, path));
    }

    public void deleteDirectory(UUID instanceId, String path) {
        Timestamp instanceCreatedAt = assertCreatedAt(instanceId);
        deleteDirectory(instanceId, instanceCreatedAt, path);
    }

    /**
     * Remove a directory and all content from it.
     */
    @WithTimer
    public void deleteDirectory(UUID instanceId, Timestamp instanceCreatedAt, String path) {
        tx(tx -> deleteDirectory(tx, instanceId, instanceCreatedAt, path));
    }

    public void delete(UUID instanceId) {
        tx(tx -> {
            Timestamp instanceCreatedAt = assertCreatedAt(tx, instanceId);
            delete(tx, instanceId, instanceCreatedAt);
        });
    }

    /**
     * Removes all process data.
     */
    @WithTimer
    public void delete(UUID instanceId, Timestamp instanceCreatedAt) {
        tx(tx -> delete(tx, instanceId, instanceCreatedAt));
    }

    private void deleteFile(DSLContext tx, UUID instanceId, Timestamp instanceCreatedAt, String path) {
        tx.deleteFrom(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(instanceCreatedAt))
                        .and(PROCESS_STATE.ITEM_PATH.eq(path)))
                .execute();
    }

    private void deleteDirectory(DSLContext tx, UUID instanceId, Timestamp instanceCreatedAt, String path) {
        tx.deleteFrom(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(instanceCreatedAt)))
                .and(PROCESS_STATE.ITEM_PATH.eq(path)
                        .or(PROCESS_STATE.ITEM_PATH.startsWith(fixPath(path))))
                .execute();
    }

    public void replace(UUID instanceId, String path, byte[] data) {
        Timestamp instanceCreatedAt = assertCreatedAt(instanceId);
        replace(instanceId, instanceCreatedAt, path, data);
    }

    /**
     * Replaces a single value.
     */
    public void replace(UUID instanceId, Timestamp instanceCreatedAt, String path, byte[] data) {
        tx(tx -> {
            deleteDirectory(tx, instanceId, instanceCreatedAt, path);
            insert(tx, instanceId, instanceCreatedAt, path, data);
        });
    }

    /**
     * Inserts a single value.
     */
    public void insert(UUID instanceId, Timestamp instanceCreatedAt, String path, byte[] data) {
        tx(tx -> insert(tx, instanceId, instanceCreatedAt, path, data));
    }

    private void insert(DSLContext tx, UUID instanceId, Timestamp instanceCreatedAt, String path, byte[] in) {
        boolean needEncrypt = secureFiles.contains(path);
        byte[] data = in;
        if (needEncrypt) {
            data = encrypt(in);
        }
        tx.insertInto(PROCESS_STATE)
                .columns(PROCESS_STATE.INSTANCE_ID, PROCESS_STATE.INSTANCE_CREATED_AT, PROCESS_STATE.ITEM_PATH, PROCESS_STATE.ITEM_DATA, PROCESS_STATE.IS_ENCRYPTED)
                .values(instanceId, instanceCreatedAt, path, data, needEncrypt)
                .execute();
    }

    public void importPath(UUID instanceId, String path, Path src) {
        Timestamp instanceCreatedAt = assertCreatedAt(instanceId);
        importPath(instanceId, instanceCreatedAt, path, src);
    }

    /**
     * Imports data from the specified directory or a file.
     *
     * @param instanceId process instance ID
     * @param path       target path prefix
     * @param src        source directory or a file
     */
    @WithTimer
    public void importPath(UUID instanceId, Timestamp instanceCreatedAt, String path, Path src) {
        tx(tx -> importPath(tx, instanceId, instanceCreatedAt, path, src, f -> true));
    }

    /**
     * Imports data from the specified directory or a file replacing the existing data.
     * If the filter function returns {@code false}, the matching file will be skipped.
     *
     * @param instanceId process instance ID
     * @param src        source directory or a file
     * @param filter     filter function
     */
    public void replacePath(UUID instanceId, Timestamp instanceCreatedAt, Path src, Function<Path, Boolean> filter) {
        tx(tx -> {
            delete(tx, instanceId, instanceCreatedAt);
            importPath(tx, instanceId, instanceCreatedAt, src, filter);
        });
    }

    private void importPath(DSLContext tx, UUID instanceId, Timestamp instanceCreatedAt, Path src, Function<Path, Boolean> filter) {
        importPath(tx, instanceId, instanceCreatedAt, null, src, filter);
    }

    private void importPath(DSLContext tx, UUID instanceId, Timestamp instanceCreatedAt, String path, Path src, Function<Path, Boolean> filter) {
        String prefix = fixPath(path);

        String sql = tx.insertInto(PROCESS_STATE)
                .columns(PROCESS_STATE.INSTANCE_ID, PROCESS_STATE.INSTANCE_CREATED_AT, PROCESS_STATE.ITEM_PATH, PROCESS_STATE.UNIX_MODE, PROCESS_STATE.ITEM_DATA, PROCESS_STATE.IS_ENCRYPTED)
                .values((UUID) null, null, null, null, null, null)
                .onConflict(PROCESS_STATE.INSTANCE_ID, PROCESS_STATE.INSTANCE_CREATED_AT, PROCESS_STATE.ITEM_PATH)
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
                        boolean needEncrypt = secureFiles.contains(n);

                        try {
                            ps.setObject(1, instanceId);
                            ps.setTimestamp(2, instanceCreatedAt);
                            ps.setString(3, n);
                            ps.setInt(4, unixMode);
                            try (InputStream in = Files.newInputStream(file);
                                 InputStream processed = needEncrypt ? encrypt(in) : in) {
                                ps.setBinaryStream(5, processed);
                            }
                            ps.setBoolean(6, needEncrypt);
                            ps.setInt(7, unixMode);
                            try (InputStream in = Files.newInputStream(file);
                                 InputStream processed = needEncrypt ? encrypt(in) : in) {
                                ps.setBinaryStream(8, processed);
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

    public boolean export(UUID instanceId, ItemConsumer consumer) {
        Timestamp instanceCreatedAt = assertCreatedAt(instanceId);
        return export(instanceId, instanceCreatedAt, consumer);
    }

    /**
     * Exports all data of a process instance.
     *
     * @param instanceId        process instance ID
     * @param instanceCreatedAt process instance creation date
     * @param consumer          a function that receives the name of a file and a data stream
     * @return {@code true} if at least a single element was exported.
     */
    public boolean export(UUID instanceId, Timestamp instanceCreatedAt, ItemConsumer consumer) {
        try (DSLContext tx = DSL.using(cfg)) {
            String sql = tx
                    .select(PROCESS_STATE.ITEM_PATH, PROCESS_STATE.UNIX_MODE, PROCESS_STATE.IS_ENCRYPTED, PROCESS_STATE.ITEM_DATA)
                    .from(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq((UUID) null).and(PROCESS_STATE.INSTANCE_CREATED_AT.eq((Timestamp) null)))
                    .getSQL();

            return tx.connectionResult(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, instanceId);
                    ps.setTimestamp(2, instanceCreatedAt);

                    boolean found = false;
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            found = true;

                            String n = rs.getString(1);
                            int unixMode = rs.getInt(2);
                            boolean encrypted = rs.getBoolean(3);
                            try (InputStream in = rs.getBinaryStream(4);
                                 InputStream processed = encrypted ? decrypt(in) : in) {
                                consumer.accept(n, unixMode, processed);
                            }
                        }
                    }

                    return found;
                }
            });
        }
    }

    public boolean exportDirectory(UUID instanceId, String path, ItemConsumer consumer) {
        Timestamp instanceCreatedAt = assertCreatedAt(instanceId);
        return exportDirectory(instanceId, instanceCreatedAt, path, consumer);
    }

    /**
     * Exports elements whose path begins with the specified value.
     *
     * @param instanceId process instance ID
     * @param path       path prefix
     * @param consumer   a function that receives the name of a file and a data stream
     * @return {@code true} if at least a single element was exported.
     */
    public boolean exportDirectory(UUID instanceId, Timestamp instanceCreatedAt, String path, ItemConsumer consumer) {
        String dir = fixPath(path);

        try (DSLContext tx = DSL.using(cfg)) {
            String sql = tx
                    .select(PROCESS_STATE.ITEM_PATH, PROCESS_STATE.UNIX_MODE, PROCESS_STATE.IS_ENCRYPTED, PROCESS_STATE.ITEM_DATA)
                    .from(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq((UUID) null)
                            .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq((Timestamp) null))
                            .and(PROCESS_STATE.ITEM_PATH.startsWith((String) null)))
                    .getSQL();

            return tx.connectionResult(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, instanceId);
                    ps.setTimestamp(2, instanceCreatedAt);
                    ps.setString(3, dir);

                    boolean found = false;
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            found = true;

                            String n = relativize(dir, rs.getString(1));
                            int unixMode = rs.getInt(2);
                            boolean encrypted = rs.getBoolean(3);
                            try (InputStream in = rs.getBinaryStream(4);
                                 InputStream processed = encrypted ? decrypt(in) : in) {
                                consumer.accept(n, unixMode, processed);
                            }
                        }
                    }

                    return found;
                }
            });
        }
    }

    private InputStream decrypt(InputStream in) {
        return SecretUtils.decrypt(in, secretCfg.getServerPwd(), secretCfg.getSecretStoreSalt());
    }

    private InputStream encrypt(InputStream in) {
        return SecretUtils.encrypt(in, secretCfg.getServerPwd(), secretCfg.getSecretStoreSalt());
    }

    private byte[] encrypt(byte[] in) {
        return SecretUtils.encrypt(in, secretCfg.getServerPwd(), secretCfg.getSecretStoreSalt());
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

    /**
     * Copies the data to the specified target directory.
     *
     * @param dst     target directory
     * @param options optional copy options
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
     */
    public static ItemConsumer copyTo(Path dst, String[] ignored, OpenOption... options) {
        return new CopyConsumer(dst, ignored, options);
    }

    /**
     * Puts all elements into the specified ZIP archive stream.
     *
     * @param dst archive stream.
     */
    public static ItemConsumer zipTo(ZipArchiveOutputStream dst) {
        return new ZipConsumer(dst);
    }

    /**
     * Creates a path from the specified array of elements.
     */
    public static String path(String... elements) {
        return String.join(PATH_SEPARATOR, elements);
    }

    public interface ItemConsumer {

        void accept(String name, int unixMode, InputStream src);
    }

    public Timestamp assertCreatedAt(UUID instanceId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return assertCreatedAt(tx, instanceId);
        }
    }

    private Timestamp assertCreatedAt(DSLContext tx, UUID instanceId) {
        Timestamp t = tx.select(PROCESS_QUEUE.CREATED_AT).from(PROCESS_QUEUE)
                .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId))
                .fetchOne(PROCESS_QUEUE.CREATED_AT);

        if (t == null) {
            throw new IllegalStateException("Process not found: " + instanceId);
        }

        return t;
    }

    public static final class CopyConsumer implements ItemConsumer {

        private final Path dst;
        private final String[] ignored;
        private final OpenOption[] options;

        private CopyConsumer(Path dst, String[] ignored, OpenOption[] options) { // NOSONAR
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
                throw new RuntimeException(e);
            }
        }
    }

    public static final class ZipConsumer implements ItemConsumer {

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
                throw new RuntimeException(e);
            }
        }
    }
}
