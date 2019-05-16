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
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.cfg.ProcessStateConfiguration;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.secret.SecretUtils;
import com.walmartlabs.concord.server.process.PartialProcessKey;
import com.walmartlabs.concord.server.process.ProcessKey;
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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.ProcessState.PROCESS_STATE;

@Named
@Singleton
public class ProcessStateManager extends AbstractDao {

    private static final String PATH_SEPARATOR = "/";

    private final SecretStoreConfiguration secretCfg;
    private final Set<String> secureFiles = new HashSet<>();

    @Inject
    protected ProcessStateManager(@MainDB Configuration cfg,
                                  SecretStoreConfiguration secretCfg,
                                  ProcessStateConfiguration stateCfg) {
        super(cfg);
        this.secretCfg = secretCfg;

        this.secureFiles.addAll(stateCfg.getSecureFiles());
    }

    public <T> Optional<T> get(PartialProcessKey partialProcessKey, String path, Function<InputStream, Optional<T>> converter) {
        ProcessKey processKey = assertKey(partialProcessKey);
        return get(processKey, path, converter);
    }

    /**
     * Fetches a single value specified by its path and applies a converter function.
     */
    public <T> Optional<T> get(ProcessKey processKey, String path, Function<InputStream, Optional<T>> converter) {
        try (DSLContext tx = DSL.using(cfg)) {
            return get(tx, processKey, path, converter);
        }
    }

    private <T> Optional<T> get(DSLContext tx, ProcessKey processKey, String path, Function<InputStream, Optional<T>> converter) {
        String sql = tx.select(PROCESS_STATE.IS_ENCRYPTED, PROCESS_STATE.ITEM_DATA)
                .from(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq((UUID) null)
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq((Timestamp) null))
                        .and(PROCESS_STATE.ITEM_PATH.eq((String) null)))
                .getSQL();

        return tx.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, processKey.getInstanceId());
                ps.setTimestamp(2, processKey.getCreatedAt());
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

    /**
     * Fetches multiple values whose path begins with the specified value and applies a converter function
     * to each value.
     */
    public <T> List<T> forEach(ProcessKey processKey, String path, Function<InputStream, Optional<T>> converter) {
        try (DSLContext tx = DSL.using(cfg)) {
            String sql = tx.select(PROCESS_STATE.IS_ENCRYPTED, PROCESS_STATE.ITEM_DATA)
                    .from(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq((UUID) null)
                            .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq((Timestamp) null))
                            .and(PROCESS_STATE.ITEM_PATH.startsWith((String) null)))
                    .getSQL();

            return tx.connectionResult(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, processKey.getInstanceId());
                    ps.setTimestamp(2, processKey.getCreatedAt());
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

    /**
     * Retrieves a list of resources whose path begins with the specified value.
     */
    public List<String> list(PartialProcessKey processKey, String path) {
        Timestamp createdAt = assertCreatedAt(processKey);

        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROCESS_STATE.ITEM_PATH)
                    .from(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq(processKey.getInstanceId())
                            .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(createdAt))
                            .and(PROCESS_STATE.ITEM_PATH.startsWith(path)))
                    .fetch(PROCESS_STATE.ITEM_PATH);
        }
    }

    /**
     * Finds all item paths that starts with the specified value.
     */
    public <T> Optional<T> findPath(ProcessKey processKey, String path, Function<Stream<String>, Optional<T>> converter) {
        try (DSLContext tx = DSL.using(cfg)) {
            Stream<String> s = tx.select(PROCESS_STATE.ITEM_PATH)
                    .from(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq(processKey.getInstanceId())
                            .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                            .and(PROCESS_STATE.ITEM_PATH.startsWith(path)))
                    .fetch(PROCESS_STATE.ITEM_PATH)
                    .stream();

            return converter.apply(s);
        }
    }

    public boolean exists(PartialProcessKey processKey, String path) {
        Timestamp instanceCreatedAt = assertCreatedAt(processKey);
        return exists(new ProcessKey(processKey, instanceCreatedAt), path);
    }

    /**
     * Checks if a value exists.
     */
    public boolean exists(ProcessKey processKey, String path) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.fetchExists(tx.selectFrom(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq(processKey.getInstanceId())
                            .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                            .and(PROCESS_STATE.ITEM_PATH.startsWith(path))));
        }
    }

    private void delete(DSLContext tx, UUID instanceId, Timestamp instanceCreatedAt) {
        tx.deleteFrom(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(instanceCreatedAt)))
                .execute();
    }

    /**
     * Removes a single value.
     */
    public void deleteFile(ProcessKey processKey, String path) {
        tx(tx -> tx.deleteFrom(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                        .and(PROCESS_STATE.ITEM_PATH.eq(path)))
                .execute());
    }

    /**
     * Remove a directory and all content from it.
     */
    @WithTimer
    public void deleteDirectory(ProcessKey processKey, String path) {
        tx(tx -> deleteDirectory(tx, processKey.getInstanceId(), processKey.getCreatedAt(), path));
    }

    public void delete(ProcessKey processKey) {
        tx(tx -> delete(tx, processKey.getInstanceId(), processKey.getCreatedAt()));
    }

    private void deleteDirectory(DSLContext tx, UUID instanceId, Timestamp instanceCreatedAt, String path) {
        tx.deleteFrom(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(instanceCreatedAt)))
                .and(PROCESS_STATE.ITEM_PATH.eq(path)
                        .or(PROCESS_STATE.ITEM_PATH.startsWith(fixPath(path))))
                .execute();
    }

    /**
     * Replaces a single value.
     */
    public void replace(ProcessKey processKey, String path, byte[] data) {
        UUID instanceId = processKey.getInstanceId();
        Timestamp instanceCreatedAt = processKey.getCreatedAt();

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

    /**
     * Imports data from the specified directory or a file.
     */
    @WithTimer
    public void importPath(ProcessKey processKey, String path, Path src) {
        tx(tx -> importPath(tx, processKey.getInstanceId(), processKey.getCreatedAt(), path, src, (p, attrs) -> true));
    }

    /**
     * Imports data from the specified directory or a file replacing the existing data.
     * If the filter function returns {@code false}, the matching file will be skipped.
     */
    public void replacePath(ProcessKey processKey, Path src, BiFunction<Path, BasicFileAttributes, Boolean> filter) {
        UUID instanceId = processKey.getInstanceId();
        Timestamp instanceCreatedAt = processKey.getCreatedAt();

        tx(tx -> {
            delete(tx, instanceId, instanceCreatedAt);
            importPath(tx, instanceId, instanceCreatedAt, null, src, filter);
        });
    }

    private void importPath(DSLContext tx, UUID instanceId, Timestamp instanceCreatedAt, String path, Path src, BiFunction<Path, BasicFileAttributes, Boolean> filter) {
        String prefix = fixPath(path);

        String sql = tx.insertInto(PROCESS_STATE)
                .columns(PROCESS_STATE.INSTANCE_ID, PROCESS_STATE.INSTANCE_CREATED_AT, PROCESS_STATE.ITEM_PATH, PROCESS_STATE.UNIX_MODE, PROCESS_STATE.ITEM_DATA, PROCESS_STATE.IS_ENCRYPTED)
                .values((UUID) null, null, null, null, null, null)
                .getSQL();

        try {
            Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path p = src.relativize(file);

                    if (!filter.apply(p, attrs)) {
                        return FileVisitResult.CONTINUE;
                    }

                    // can't import directories or symlinks
                    // the caller shouldn't attempt to import anything but regular files
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IllegalStateException("Can't import non-regular files into the process state: " + p +
                                " This is most likely a bug.");
                    }

                    String n = p.toString();
                    if (prefix != null) {
                        n = prefix + n;
                    }

                    Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
                    int unixMode = Posix.unixMode(permissions);
                    boolean needsEncryption = secureFiles.contains(n);

                    tx.deleteFrom(PROCESS_STATE).where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                            .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(instanceCreatedAt))
                            .and(PROCESS_STATE.ITEM_PATH.eq(n)))
                            .execute();

                    String itemPath = n;
                    tx.connection(conn -> {
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            // INSTANCE_ID
                            ps.setObject(1, instanceId);

                            // INSTANCE_CREATED_AT
                            ps.setTimestamp(2, instanceCreatedAt);

                            // ITEM_PATH
                            ps.setString(3, itemPath);

                            // UNIX_MODE
                            ps.setInt(4, unixMode);

                            // ITEM_DATA
                            try (InputStream in = Files.newInputStream(file);
                                 InputStream processed = needsEncryption ? encrypt(in) : in) {
                                ps.setBinaryStream(5, processed);
                            }

                            // IS_ENCRYPTED
                            ps.setBoolean(6, needsEncryption);

                            ps.execute();
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Exports all data of a process instance.
     */
    public boolean export(ProcessKey processKey, ItemConsumer consumer) {
        try (DSLContext tx = DSL.using(cfg)) {
            String sql = tx
                    .select(PROCESS_STATE.ITEM_PATH, PROCESS_STATE.UNIX_MODE, PROCESS_STATE.IS_ENCRYPTED, PROCESS_STATE.ITEM_DATA)
                    .from(PROCESS_STATE)
                    .where(PROCESS_STATE.INSTANCE_ID.eq((UUID) null).and(PROCESS_STATE.INSTANCE_CREATED_AT.eq((Timestamp) null)))
                    .getSQL();

            return tx.connectionResult(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, processKey.getInstanceId());
                    ps.setTimestamp(2, processKey.getCreatedAt());

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

    /**
     * Exports elements whose path begins with the specified value.
     */
    public boolean exportDirectory(ProcessKey processKey, String path, ItemConsumer consumer) {
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
                    ps.setObject(1, processKey.getInstanceId());
                    ps.setTimestamp(2, processKey.getCreatedAt());
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

    public static ItemConsumer exclude(ItemConsumer delegate, String... patterns) {
        return new FilteringConsumer(delegate, n -> Arrays.stream(patterns).noneMatch(n::matches));
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

    public ProcessKey assertKey(PartialProcessKey processKey) {
        try (DSLContext tx = DSL.using(cfg)) {
            Timestamp createdAt = assertCreatedAt(tx, processKey.getInstanceId());
            return new ProcessKey(processKey, createdAt);
        }
    }

    public Timestamp assertCreatedAt(PartialProcessKey processKey) {
        return assertCreatedAt(processKey.getInstanceId());
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

    public static final class FilteringConsumer implements ItemConsumer {

        private final ItemConsumer delegate;
        private final Function<String, Boolean> checkFn;

        public FilteringConsumer(ItemConsumer delegate, Function<String, Boolean> checkFn) {
            this.delegate = delegate;
            this.checkFn = checkFn;
        }

        @Override
        public void accept(String name, int unixMode, InputStream src) {
            if (checkFn.apply(name)) {
                delegate.accept(name, unixMode, src);
            }
        }
    }
}
