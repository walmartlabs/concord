package com.walmartlabs.concord.server.process.state;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.common.secret.SecretUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.StatePolicy;
import com.walmartlabs.concord.policyengine.StateRule;
import com.walmartlabs.concord.server.cfg.ProcessConfiguration;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.policy.PolicyException;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKeyCache;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_INITIAL_STATE;
import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.ProcessState.PROCESS_STATE;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static org.jooq.impl.DSL.*;

public class ProcessStateManager extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(ProcessStateManager.class);

    private static final String PATH_SEPARATOR = "/";
    private static final String FORMS_PATH_PREFIX = "forms/";
    private static final int INSERT_BATCH_SIZE = 10;
    private static final String FORM_STATE_TABLE_NAME = "process_form_state";
    private static final String FORM_STATE_ITEMS_TABLE_NAME = "process_form_state_items";

    private static final Table<?> FORM_STATE = table(name(FORM_STATE_TABLE_NAME));
    private static final Field<UUID> FORM_STATE_INSTANCE_ID = field(name(FORM_STATE_TABLE_NAME, "instance_id"), UUID.class);
    private static final Field<OffsetDateTime> FORM_STATE_INSTANCE_CREATED_AT = field(name(FORM_STATE_TABLE_NAME, "instance_created_at"), OffsetDateTime.class);
    private static final Field<String> FORM_STATE_ITEM_PATH = field(name(FORM_STATE_TABLE_NAME, "item_path"), String.class);
    private static final Field<Short> FORM_STATE_UNIX_MODE = field(name(FORM_STATE_TABLE_NAME, "unix_mode"), Short.class);
    private static final Field<String> FORM_STATE_ITEM_HASH = field(name(FORM_STATE_TABLE_NAME, "item_hash"), String.class);

    private static final Table<?> FORM_STATE_ITEMS = table(name(FORM_STATE_ITEMS_TABLE_NAME));
    private static final Field<String> FORM_STATE_ITEMS_ITEM_HASH = field(name(FORM_STATE_ITEMS_TABLE_NAME, "item_hash"), String.class);
    private static final Field<byte[]> FORM_STATE_ITEMS_ITEM_DATA = field(name(FORM_STATE_ITEMS_TABLE_NAME, "item_data"), byte[].class);
    private static final Field<Boolean> FORM_STATE_ITEMS_IS_ENCRYPTED = field(name(FORM_STATE_ITEMS_TABLE_NAME, "is_encrypted"), Boolean.class);

    private final SecretStoreConfiguration secretCfg;
    private final PolicyManager policyManager;
    private final ProcessLogManager logManager;
    private final ProcessKeyCache processKeyCache;

    private final Set<String> secureFiles;

    @Inject
    protected ProcessStateManager(@MainDB Configuration cfg,
                                  SecretStoreConfiguration secretCfg,
                                  ProcessConfiguration stateCfg,
                                  PolicyManager policyManager,
                                  ProcessLogManager logManager,
                                  ProcessKeyCache processKeyCache) {
        super(cfg);
        this.secretCfg = secretCfg;
        this.policyManager = policyManager;
        this.logManager = logManager;
        this.processKeyCache = processKeyCache;

        this.secureFiles = Collections.unmodifiableSet(new HashSet<>(stateCfg.getSecureFiles()));
    }

    @Override
    public void tx(Tx t) {
        super.tx(t);
    }

    public <T> Optional<T> get(PartialProcessKey partialProcessKey, String path, Function<InputStream, Optional<T>> converter) {
        ProcessKey processKey = processKeyCache.get(partialProcessKey.getInstanceId());
        if (processKey == null) {
            throw new IllegalStateException("Can't determine the process key of " + partialProcessKey.getInstanceId());
        }
        return get(processKey, path, converter);
    }

    /**
     * Fetches a single value specified by its path and applies a converter function.
     */
    public <T> Optional<T> get(ProcessKey processKey, String path, Function<InputStream, Optional<T>> converter) {
        return get(dsl(), processKey, path, converter);
    }

    private <T> Optional<T> get(DSLContext tx, ProcessKey processKey, String path, Function<InputStream, Optional<T>> converter) {
        return doGet(tx, CurrentProcessStateTable.INSTANCE, processKey, path, converter);
    }

    public <T> Optional<T> getInitial(ProcessKey processKey, String path, Function<InputStream, Optional<T>> converter) {
        return getInitial(dsl(), processKey, path, converter);
    }

    private <T> Optional<T> getInitial(DSLContext tx, ProcessKey processKey, String path, Function<InputStream, Optional<T>> converter) {
        return doGet(tx, InitialProcessStateTable.INSTANCE, processKey, path, converter);
    }

    private <T> Optional<T> doGet(DSLContext tx, ProcessStateTable table, ProcessKey processKey, String path, Function<InputStream, Optional<T>> converter) {
        Optional<T> result = doGetFromStateTable(tx, table, processKey, path, converter);
        if (result.isPresent()) {
            return result;
        }

        if (isCurrentProcessState(table) && isFormResource(path)) {
            return doGetFormState(tx, processKey, path, converter);
        }

        return Optional.empty();
    }

    private <T> Optional<T> doGetFromStateTable(DSLContext tx, ProcessStateTable table, ProcessKey processKey, String path, Function<InputStream, Optional<T>> converter) {
        String sql = tx.select(table.IS_ENCRYPTED(), table.ITEM_DATA())
                .from(table.table())
                .where(table.INSTANCE_ID().eq((UUID) null)
                        .and(table.INSTANCE_CREATED_AT().eq((OffsetDateTime) null))
                        .and(table.ITEM_PATH().eq((String) null)))
                .getSQL();

        return tx.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, processKey.getInstanceId());
                ps.setObject(2, processKey.getCreatedAt());
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

    private <T> Optional<T> doGetFormState(DSLContext tx, ProcessKey processKey, String path, Function<InputStream, Optional<T>> converter) {
        String sql = tx.select(FORM_STATE_ITEMS_IS_ENCRYPTED, FORM_STATE_ITEMS_ITEM_DATA)
                .from(FORM_STATE.join(FORM_STATE_ITEMS).on(FORM_STATE_ITEM_HASH.eq(FORM_STATE_ITEMS_ITEM_HASH)))
                .where(FORM_STATE_INSTANCE_ID.eq((UUID) null)
                        .and(FORM_STATE_INSTANCE_CREATED_AT.eq((OffsetDateTime) null))
                        .and(FORM_STATE_ITEM_PATH.eq((String) null)))
                .getSQL();

        return tx.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, processKey.getInstanceId());
                ps.setObject(2, processKey.getCreatedAt());
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
        DSLContext tx = dsl();

        String sql = tx.select(PROCESS_STATE.IS_ENCRYPTED, PROCESS_STATE.ITEM_DATA)
                .from(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq((UUID) null)
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq((OffsetDateTime) null))
                        .and(PROCESS_STATE.ITEM_PATH.startsWith((String) null)))
                .getSQL();

        return tx.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, processKey.getInstanceId());
                ps.setObject(2, processKey.getCreatedAt());
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

                String formSql = tx.select(FORM_STATE_ITEMS_IS_ENCRYPTED, FORM_STATE_ITEMS_ITEM_DATA)
                        .from(FORM_STATE.join(FORM_STATE_ITEMS).on(FORM_STATE_ITEM_HASH.eq(FORM_STATE_ITEMS_ITEM_HASH)))
                        .where(FORM_STATE_INSTANCE_ID.eq((UUID) null)
                                .and(FORM_STATE_INSTANCE_CREATED_AT.eq((OffsetDateTime) null))
                                .and(FORM_STATE_ITEM_PATH.startsWith((String) null)))
                        .getSQL();

                try (PreparedStatement formPs = conn.prepareStatement(formSql)) {
                    formPs.setObject(1, processKey.getInstanceId());
                    formPs.setObject(2, processKey.getCreatedAt());
                    formPs.setString(3, path);

                    try (ResultSet rs = formPs.executeQuery()) {
                        while (rs.next()) {
                            boolean encrypted = rs.getBoolean(1);
                            try (InputStream in = rs.getBinaryStream(2);
                                 InputStream processed = encrypted ? decrypt(in) : in) {
                                Optional<T> o = converter.apply(processed);
                                o.ifPresent(result::add);
                            }
                        }
                    }
                }

                return result;
            }
        });
    }

    /**
     * Retrieves a list of resources whose path begins with the specified value.
     */
    public List<String> list(PartialProcessKey partialProcessKey, String path) {
        ProcessKey processKey = processKeyCache.assertKey(partialProcessKey.getInstanceId());
        List<String> result = dsl().select(PROCESS_STATE.ITEM_PATH)
                .from(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                        .and(PROCESS_STATE.ITEM_PATH.startsWith(path)))
                .fetch(PROCESS_STATE.ITEM_PATH);

        result.addAll(dsl().select(FORM_STATE_ITEM_PATH)
                .from(FORM_STATE)
                .where(FORM_STATE_INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(FORM_STATE_INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                        .and(FORM_STATE_ITEM_PATH.startsWith(path)))
                .fetch(FORM_STATE_ITEM_PATH));
        return result;
    }

    /**
     * Finds all item paths that starts with the specified value.
     */
    public <T> Optional<T> findPath(ProcessKey processKey, String path, Function<Stream<String>, Optional<T>> converter) {
        List<String> result = dsl().select(PROCESS_STATE.ITEM_PATH)
                .from(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                        .and(PROCESS_STATE.ITEM_PATH.startsWith(path)))
                .fetch(PROCESS_STATE.ITEM_PATH);

        result.addAll(dsl().select(FORM_STATE_ITEM_PATH)
                .from(FORM_STATE)
                .where(FORM_STATE_INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(FORM_STATE_INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                        .and(FORM_STATE_ITEM_PATH.startsWith(path)))
                .fetch(FORM_STATE_ITEM_PATH));

        return converter.apply(result.stream());
    }

    public boolean exists(PartialProcessKey partialProcessKey, String path) {
        ProcessKey processKey = processKeyCache.get(partialProcessKey.getInstanceId());
        return exists(processKey, path);
    }

    /**
     * Checks if a value exists.
     */
    public boolean exists(ProcessKey processKey, String path) {
        boolean exists = dsl().fetchExists(selectFrom(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                        .and(PROCESS_STATE.ITEM_PATH.startsWith(path))));

        if (exists) {
            return true;
        }

        return dsl().fetchExists(selectFrom(FORM_STATE)
                .where(FORM_STATE_INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(FORM_STATE_INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                        .and(FORM_STATE_ITEM_PATH.startsWith(path))));
    }

    /**
     * Removes a single value.
     */
    public void deleteFile(ProcessKey processKey, String path) {
        tx(tx -> deleteFile(tx, processKey, path));
    }

    public void deleteFile(DSLContext tx, ProcessKey processKey, String path) {
        tx.deleteFrom(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                        .and(PROCESS_STATE.ITEM_PATH.eq(path)))
                .execute();

        if (isFormResource(path)) {
            deleteFormState(tx, FORM_STATE_INSTANCE_ID.eq(processKey.getInstanceId())
                    .and(FORM_STATE_INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                    .and(FORM_STATE_ITEM_PATH.eq(path)));
        }
    }

    public void delete(ProcessKey processKey) {
        tx(tx -> delete(tx, processKey));
    }

    public void delete(DSLContext tx, ProcessKey processKey) {
        delete(tx, processKey.getInstanceId(), processKey.getCreatedAt());
    }

    /**
     * Remove a directory and all content from it.
     */
    @WithTimer
    public void deleteDirectory(DSLContext tx, ProcessKey processKey, String path) {
        tx.deleteFrom(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())))
                .and(PROCESS_STATE.ITEM_PATH.eq(path)
                        .or(PROCESS_STATE.ITEM_PATH.startsWith(fixPath(path))))
                .execute();

        if (isFormPath(path)) {
            deleteFormState(tx, FORM_STATE_INSTANCE_ID.eq(processKey.getInstanceId())
                    .and(FORM_STATE_INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                    .and(FORM_STATE_ITEM_PATH.eq(path)
                            .or(FORM_STATE_ITEM_PATH.startsWith(fixPath(path)))));
        }
    }

    /**
     * Replaces a single value.
     */
    public void replace(ProcessKey processKey, String path, byte[] data) {
        tx(tx -> {
            deleteDirectory(tx, processKey, path);
            insert(tx, processKey, path, data);
        });
    }

    /**
     * Inserts a single value.
     */
    public void insert(DSLContext tx, ProcessKey processKey, String path, byte[] in) {
        doInsert(tx, CurrentProcessStateTable.INSTANCE, processKey, path, in);
    }

    public void insertInitial(DSLContext tx, ProcessKey processKey, String path, byte[] in) {
        doInsert(tx, InitialProcessStateTable.INSTANCE, processKey, path, in);
    }

    private void doInsert(DSLContext tx, ProcessStateTable table, ProcessKey processKey, String path, byte[] in) {
        boolean needEncrypt = secureFiles.contains(path);
        byte[] data = in;
        if (needEncrypt) {
            data = encrypt(in);
        }
        tx.insertInto(table.table())
                .columns(table.INSTANCE_ID(), table.INSTANCE_CREATED_AT(), table.ITEM_PATH(), table.ITEM_DATA(), table.IS_ENCRYPTED())
                .values(processKey.getInstanceId(), processKey.getCreatedAt(), path, data, needEncrypt)
                .execute();
    }

    /**
     * Imports data from the specified directory or a file replacing the existing data.
     * If the filter function returns {@code false}, the matching file will be skipped.
     */
    public void replacePath(ProcessKey processKey, Path src, BiFunction<Path, BasicFileAttributes, Boolean> filter) {
        UUID instanceId = processKey.getInstanceId();
        OffsetDateTime instanceCreatedAt = processKey.getCreatedAt();

        tx(tx -> {
            delete(tx, instanceId, instanceCreatedAt);
            importPath(tx, processKey, null, src, filter);
        });
    }

    /**
     * Imports data from the specified directory or a file.
     */
    @WithTimer
    public void importPath(ProcessKey processKey, String path, Path src, BiFunction<Path, BasicFileAttributes, Boolean> filter) {
        tx(tx -> importPath(tx, processKey, path, src, filter));
    }

    @WithTimer
    public void importPath(DSLContext tx, ProcessKey processKey, String path, Path src, BiFunction<Path, BasicFileAttributes, Boolean> filter) {
        doImportPath(tx, CurrentProcessStateTable.INSTANCE, processKey, path, src, filter);
    }

    @WithTimer
    public void importPathInitial(DSLContext tx, ProcessKey processKey, String path, Path src, BiFunction<Path, BasicFileAttributes, Boolean> filter) {
        doImportPath(tx, InitialProcessStateTable.INSTANCE, processKey, path, src, filter);
    }

    private void doImportPath(DSLContext tx, ProcessStateTable table, ProcessKey processKey, String path, Path src, BiFunction<Path, BasicFileAttributes, Boolean> filter) {
        PolicyEngine policyEngine = assertPolicy(tx, processKey, src, filter);

        String prefix = fixPath(path);

        List<BatchItem> batch = new ArrayList<>();
        List<FormBatchItem> formBatch = new ArrayList<>();
        try {
            Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!filter.apply(file, attrs)) {
                        return FileVisitResult.CONTINUE;
                    }

                    Path p = src.relativize(file);

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

                    if (isCurrentProcessState(table) && isFormResource(n)) {
                        tx.deleteFrom(table.table()).where(table.INSTANCE_ID().eq(processKey.getInstanceId())
                                        .and(table.INSTANCE_CREATED_AT().eq(processKey.getCreatedAt()))
                                        .and(table.ITEM_PATH().eq(n)))
                                .execute();

                        deleteFormState(tx, FORM_STATE_INSTANCE_ID.eq(processKey.getInstanceId())
                                .and(FORM_STATE_INSTANCE_CREATED_AT.eq(processKey.getCreatedAt()))
                                .and(FORM_STATE_ITEM_PATH.eq(n)));

                        formBatch.add(new FormBatchItem(n, file, unixMode, needsEncryption));
                        if (formBatch.size() >= INSERT_BATCH_SIZE) {
                            insertFormState(tx, processKey, formBatch);
                            formBatch.clear();
                        }
                    } else {
                        tx.deleteFrom(table.table()).where(table.INSTANCE_ID().eq(processKey.getInstanceId())
                                        .and(table.INSTANCE_CREATED_AT().eq(processKey.getCreatedAt()))
                                        .and(table.ITEM_PATH().eq(n)))
                                .execute();

                        batch.add(new BatchItem(n, file, unixMode, needsEncryption));
                        if (batch.size() >= INSERT_BATCH_SIZE) {
                            doInsert(tx, table, processKey.getInstanceId(), processKey.getCreatedAt(), batch);
                            batch.clear();
                        }
                    }

                    return FileVisitResult.CONTINUE;
                }
            });

            if (!batch.isEmpty()) {
                doInsert(tx, table, processKey.getInstanceId(), processKey.getCreatedAt(), batch);
            }

            if (!formBatch.isEmpty()) {
                insertFormState(tx, processKey, formBatch);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        assertPolicy(tx, processKey, policyEngine);
    }

    /**
     * Exports all data of a process instance.
     */
    public boolean export(ProcessKey processKey, ItemConsumer consumer) {
        DSLContext tx = dsl();

        String sql = tx
                .select(PROCESS_STATE.ITEM_PATH, PROCESS_STATE.UNIX_MODE, PROCESS_STATE.IS_ENCRYPTED, PROCESS_STATE.ITEM_DATA)
                .from(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq((UUID) null).and(PROCESS_STATE.INSTANCE_CREATED_AT.eq((OffsetDateTime) null)))
                .getSQL();

        return tx.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, processKey.getInstanceId());
                ps.setObject(2, processKey.getCreatedAt());

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

                String formSql = tx.select(FORM_STATE_ITEM_PATH, FORM_STATE_UNIX_MODE, FORM_STATE_ITEMS_IS_ENCRYPTED, FORM_STATE_ITEMS_ITEM_DATA)
                        .from(FORM_STATE.join(FORM_STATE_ITEMS).on(FORM_STATE_ITEM_HASH.eq(FORM_STATE_ITEMS_ITEM_HASH)))
                        .where(FORM_STATE_INSTANCE_ID.eq((UUID) null).and(FORM_STATE_INSTANCE_CREATED_AT.eq((OffsetDateTime) null)))
                        .getSQL();

                try (PreparedStatement formPs = conn.prepareStatement(formSql)) {
                    formPs.setObject(1, processKey.getInstanceId());
                    formPs.setObject(2, processKey.getCreatedAt());

                    try (ResultSet rs = formPs.executeQuery()) {
                        while (rs.next()) {
                            found = true;

                            String n = rs.getString(1);
                            int unixMode = rs.getShort(2);
                            boolean encrypted = rs.getBoolean(3);
                            try (InputStream in = rs.getBinaryStream(4);
                                 InputStream processed = encrypted ? decrypt(in) : in) {
                                consumer.accept(n, unixMode, processed);
                            }
                        }
                    }
                }

                return found;
            }
        });
    }

    /**
     * Exports elements whose path begins with the specified value.
     */
    public boolean exportDirectory(ProcessKey processKey, String path, ItemConsumer consumer) {
        return doExportDirectory(CurrentProcessStateTable.INSTANCE, processKey, path, consumer);
    }

    public boolean exportDirectoryInitial(ProcessKey processKey, String path, ItemConsumer consumer) {
        return doExportDirectory(InitialProcessStateTable.INSTANCE, processKey, path, consumer);
    }

    public boolean doExportDirectory(ProcessStateTable table, ProcessKey processKey, String path, ItemConsumer consumer) {
        String dir = fixPath(path);

        DSLContext tx = dsl();

        String sql = tx
                .select(table.ITEM_PATH(), table.UNIX_MODE(), table.IS_ENCRYPTED(), table.ITEM_DATA())
                .from(table.table())
                .where(table.INSTANCE_ID().eq((UUID) null)
                        .and(table.INSTANCE_CREATED_AT().eq((OffsetDateTime) null))
                        .and(table.ITEM_PATH().startsWith((String) null)))
                .getSQL();

        return tx.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, processKey.getInstanceId());
                ps.setObject(2, processKey.getCreatedAt());
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

                if (isCurrentProcessState(table) && isFormPath(path)) {
                    String formSql = tx.select(FORM_STATE_ITEM_PATH, FORM_STATE_UNIX_MODE, FORM_STATE_ITEMS_IS_ENCRYPTED, FORM_STATE_ITEMS_ITEM_DATA)
                            .from(FORM_STATE.join(FORM_STATE_ITEMS).on(FORM_STATE_ITEM_HASH.eq(FORM_STATE_ITEMS_ITEM_HASH)))
                            .where(FORM_STATE_INSTANCE_ID.eq((UUID) null)
                                    .and(FORM_STATE_INSTANCE_CREATED_AT.eq((OffsetDateTime) null))
                                    .and(FORM_STATE_ITEM_PATH.startsWith((String) null)))
                            .getSQL();

                    try (PreparedStatement formPs = conn.prepareStatement(formSql)) {
                        formPs.setObject(1, processKey.getInstanceId());
                        formPs.setObject(2, processKey.getCreatedAt());
                        formPs.setString(3, dir);

                        try (ResultSet rs = formPs.executeQuery()) {
                            while (rs.next()) {
                                found = true;

                                String n = relativize(dir, rs.getString(1));
                                int unixMode = rs.getShort(2);
                                boolean encrypted = rs.getBoolean(3);
                                try (InputStream in = rs.getBinaryStream(4);
                                     InputStream processed = encrypted ? decrypt(in) : in) {
                                    consumer.accept(n, unixMode, processed);
                                }
                            }
                        }
                    }
                }

                return found;
            }
        });
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

    private void delete(DSLContext tx, UUID instanceId, OffsetDateTime instanceCreatedAt) {
        deleteFormState(tx, FORM_STATE_INSTANCE_ID.eq(instanceId)
                .and(FORM_STATE_INSTANCE_CREATED_AT.eq(instanceCreatedAt)));

        tx.deleteFrom(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(instanceId)
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(instanceCreatedAt)))
                .execute();
    }

    private void insert(DSLContext tx, UUID instanceId, OffsetDateTime instanceCreatedAt, Collection<BatchItem> batch) {
        String sql = tx.insertInto(PROCESS_STATE)
                .columns(PROCESS_STATE.INSTANCE_ID, PROCESS_STATE.INSTANCE_CREATED_AT, PROCESS_STATE.ITEM_PATH, PROCESS_STATE.UNIX_MODE, PROCESS_STATE.ITEM_DATA, PROCESS_STATE.IS_ENCRYPTED)
                .values((UUID) null, null, null, null, null, null)
                .getSQL();

        List<InputStream> streams = new LinkedList<>();
        try {
            tx.connection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (BatchItem item : batch) {
                        // INSTANCE_ID
                        ps.setObject(1, instanceId);

                        // INSTANCE_CREATED_AT
                        ps.setObject(2, instanceCreatedAt);

                        // ITEM_PATH
                        ps.setString(3, item.itemPath);

                        // UNIX_MODE
                        ps.setInt(4, item.unixMode);

                        InputStream in = Files.newInputStream(item.path);
                        streams.add(in); // keep the streams open until the batch is committed

                        if (item.needsEncryption) {
                            in = encrypt(in);
                        }

                        // ITEM_DATA
                        ps.setBinaryStream(5, in);

                        // IS_ENCRYPTED
                        ps.setBoolean(6, item.needsEncryption);

                        ps.addBatch();
                    }

                    ps.executeBatch();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            streams.forEach(ProcessStateManager::closeSilently);
        }
    }

    private void doInsert(DSLContext tx, ProcessStateTable table, UUID instanceId, OffsetDateTime instanceCreatedAt, Collection<BatchItem> batch) {
        String sql = tx.insertInto(table.table())
                .columns(table.INSTANCE_ID(), table.INSTANCE_CREATED_AT(), table.ITEM_PATH(), table.UNIX_MODE(), table.ITEM_DATA(), table.IS_ENCRYPTED())
                .values((UUID) null, null, null, null, null, null)
                .getSQL();

        List<InputStream> streams = new LinkedList<>();
        try {
            tx.connection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (BatchItem item : batch) {
                        // INSTANCE_ID
                        ps.setObject(1, instanceId);

                        // INSTANCE_CREATED_AT
                        ps.setObject(2, instanceCreatedAt);

                        // ITEM_PATH
                        ps.setString(3, item.itemPath);

                        // UNIX_MODE
                        ps.setInt(4, item.unixMode);

                        InputStream in = Files.newInputStream(item.path);
                        streams.add(in); // keep the streams open until the batch is committed

                        if (item.needsEncryption) {
                            in = encrypt(in);
                        }

                        // ITEM_DATA
                        ps.setBinaryStream(5, in);

                        // IS_ENCRYPTED
                        ps.setBoolean(6, item.needsEncryption);

                        ps.addBatch();
                    }

                    ps.executeBatch();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            streams.forEach(ProcessStateManager::closeSilently);
        }
    }

    private void insertFormState(DSLContext tx, ProcessKey processKey, Collection<FormBatchItem> batch) {
        String upsertItemSql = tx.insertInto(FORM_STATE_ITEMS)
                .columns(FORM_STATE_ITEMS_ITEM_HASH, FORM_STATE_ITEMS_ITEM_DATA, FORM_STATE_ITEMS_IS_ENCRYPTED)
                .values((String) null, (byte[]) null, null)
                .onConflict(FORM_STATE_ITEMS_ITEM_HASH)
                .doNothing()
                .getSQL();

        String insertRefSql = tx.insertInto(FORM_STATE)
                .columns(FORM_STATE_INSTANCE_ID, FORM_STATE_INSTANCE_CREATED_AT, FORM_STATE_ITEM_PATH, FORM_STATE_UNIX_MODE, FORM_STATE_ITEM_HASH)
                .values((UUID) null, null, null, null, null)
                .getSQL();

        tx.connection(conn -> {
            try (PreparedStatement upsertItemPs = conn.prepareStatement(upsertItemSql);
                 PreparedStatement insertRefPs = conn.prepareStatement(insertRefSql)) {
                for (FormBatchItem item : batch) {
                    byte[] data = Files.readAllBytes(item.path);
                    String itemHash = itemHash(data, item.needsEncryption);
                    byte[] stored = data;
                    if (item.needsEncryption) {
                        stored = encrypt(data);
                    }

                    upsertItemPs.setString(1, itemHash);
                    upsertItemPs.setBytes(2, stored);
                    upsertItemPs.setBoolean(3, item.needsEncryption);
                    upsertItemPs.addBatch();

                    insertRefPs.setObject(1, processKey.getInstanceId());
                    insertRefPs.setObject(2, processKey.getCreatedAt());
                    insertRefPs.setString(3, item.itemPath);
                    insertRefPs.setInt(4, item.unixMode);
                    insertRefPs.setString(5, itemHash);
                    insertRefPs.addBatch();
                }

                upsertItemPs.executeBatch();
                insertRefPs.executeBatch();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException("Error while reading form state file", e);
            }
        });
    }

    private void deleteFormState(DSLContext tx, org.jooq.Condition condition) {
        List<String> affected = tx.select(FORM_STATE_ITEM_HASH)
                .from(FORM_STATE)
                .where(condition)
                .fetch(FORM_STATE_ITEM_HASH);

        if (affected.isEmpty()) {
            return;
        }

        tx.deleteFrom(FORM_STATE)
                .where(condition)
                .execute();

        cleanupUnreferencedFormStateItems(tx, affected);
    }

    private void cleanupUnreferencedFormStateItems(DSLContext tx, Collection<String> itemHashes) {
        if (itemHashes == null || itemHashes.isEmpty()) {
            return;
        }

        tx.deleteFrom(FORM_STATE_ITEMS)
                .where(FORM_STATE_ITEMS_ITEM_HASH.in(itemHashes)
                        .andNotExists(selectOne()
                                .from(FORM_STATE)
                                .where(FORM_STATE_ITEM_HASH.eq(FORM_STATE_ITEMS_ITEM_HASH))))
                .execute();
    }

    private static boolean isFormResource(String path) {
        return path != null && path.startsWith(FORMS_PATH_PREFIX);
    }

    private static boolean isFormPath(String path) {
        return FORMS_PATH_PREFIX.substring(0, FORMS_PATH_PREFIX.length() - 1).equals(path) || isFormResource(path);
    }

    private static boolean isCurrentProcessState(ProcessStateTable table) {
        return table == CurrentProcessStateTable.INSTANCE;
    }

    private static String itemHash(byte[] data, boolean encrypted) {
        return (encrypted ? "enc:" : "raw:") + sha256Hex(data);
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 digest is not available", e);
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

    private PolicyEngine assertPolicy(DSLContext tx, ProcessKey processKey, Path src, BiFunction<Path, BasicFileAttributes, Boolean> filter) {
        PolicyEngine pe = getPolicyEngine(tx, processKey);
        if (pe == null) {
            return null;
        }

        CheckResult<StateRule, Path> result;
        try {
            result = pe.getStatePolicy().check(src, filter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        result.getWarn().forEach(w -> logManager.warn(processKey, "Potentially restricted state file '{}' (state policy: {})", src.relativize(w.getEntity()), w.getRule().msg()));
        result.getDeny().forEach(e -> logManager.error(processKey, "State file '{}' is forbidden by the state policy {}", src.relativize(e.getEntity()), e.getRule().msg()));

        if (!result.getDeny().isEmpty()) {
            throw new PolicyException("Found forbidden state files");
        }

        return pe;
    }

    private void assertPolicy(DSLContext tx, ProcessKey processKey, PolicyEngine policyEngine) {
        if (policyEngine == null) {
            return;
        }

        CheckResult<StateRule, StatePolicy.StateStats> result = policyEngine.getStatePolicy().check(() -> getStateStats(tx, processKey));

        result.getWarn().forEach(w -> logManager.warn(processKey, "Potentially restricted state: '{}' (state policy: {})", w.getMsg(), w.getRule().msg()));
        result.getDeny().forEach(e -> logManager.error(processKey, "State is forbidden: '{}' (state policy {})", e.getMsg(), e.getRule().msg()));

        if (!result.getDeny().isEmpty()) {
            throw new PolicyException("Found forbidden state files");
        }
    }

    private static StatePolicy.StateStats getStateStats(DSLContext tx, ProcessKey processKey) {
        StatePolicy.StateStats processStateStats = tx.select(coalesce(DSL.sum(PgUtils.length(PROCESS_STATE.ITEM_DATA)), 0L), count(PROCESS_STATE.ITEM_DATA))
                .from(PROCESS_STATE)
                .where(PROCESS_STATE.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_STATE.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())))
                .fetchOne(r -> new StatePolicy.StateStats(((Number) r.value1()).longValue(), r.value2()));

        StatePolicy.StateStats formsStateStats = tx.select(coalesce(DSL.sum(PgUtils.length(FORM_STATE_ITEMS_ITEM_DATA)), 0L), count(FORM_STATE_ITEM_PATH))
                .from(FORM_STATE.join(FORM_STATE_ITEMS).on(FORM_STATE_ITEM_HASH.eq(FORM_STATE_ITEMS_ITEM_HASH)))
                .where(FORM_STATE_INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(FORM_STATE_INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())))
                .fetchOne(r -> new StatePolicy.StateStats(((Number) r.value1()).longValue(), r.value2()));

        return new StatePolicy.StateStats(processStateStats.getSize() + formsStateStats.getSize(),
                processStateStats.getFilesCount() + formsStateStats.getFilesCount());
    }

    private PolicyEngine getPolicyEngine(DSLContext tx, ProcessKey processKey) {
        Field<UUID> orgId = select(PROJECTS.ORG_ID)
                .from(PROJECTS)
                .where(PROJECTS.PROJECT_ID.eq(PROCESS_QUEUE.PROJECT_ID))
                .asField();

        Map<String, UUID> info = tx.select(orgId, PROCESS_QUEUE.PROJECT_ID, PROCESS_QUEUE.INITIATOR_ID)
                .from(PROCESS_QUEUE)
                .where(PROCESS_QUEUE.INSTANCE_ID.eq(processKey.getInstanceId()))
                .fetchOne(r -> {
                    Map<String, UUID> result = new HashMap<>();
                    result.put("orgId", r.value1());
                    result.put("prjId", r.value2());
                    result.put("userId", r.value3());
                    return result;
                });
        if (info == null) {
            return null;
        }

        return policyManager.get(info.get("orgId"), info.get("prjId"), info.get("userId"));
    }

    private static void closeSilently(AutoCloseable c) {
        if (c == null) {
            return;
        }

        try {
            c.close();
        } catch (Exception e) {
            log.error("Error while closing a resource", e);
        }
    }

    public interface ItemConsumer {

        void accept(String name, int unixMode, InputStream src);
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
                    src.transferTo(dst);
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
                src.transferTo(dst);
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

    private static final class BatchItem {

        private final String itemPath;
        private final Path path;
        private final int unixMode;
        private final boolean needsEncryption;

        private BatchItem(String itemPath, Path path, int unixMode, boolean needsEncryption) {
            this.itemPath = itemPath;
            this.path = path;
            this.unixMode = unixMode;
            this.needsEncryption = needsEncryption;
        }
    }

    private static final class FormBatchItem {

        private final String itemPath;
        private final Path path;
        private final int unixMode;
        private final boolean needsEncryption;

        private FormBatchItem(String itemPath, Path path, int unixMode, boolean needsEncryption) {
            this.itemPath = itemPath;
            this.path = path;
            this.unixMode = unixMode;
            this.needsEncryption = needsEncryption;
        }
    }

    private interface ProcessStateTable {

        Table<?> table();

        TableField<?, UUID> INSTANCE_ID();

        TableField<?, OffsetDateTime> INSTANCE_CREATED_AT();

        TableField<?, String> ITEM_PATH();

        TableField<?, byte[]> ITEM_DATA();

        TableField<?, Short> UNIX_MODE();

        TableField<?, Boolean> IS_ENCRYPTED();
    }


    static class InitialProcessStateTable implements ProcessStateTable {

        public static InitialProcessStateTable INSTANCE = new InitialProcessStateTable();

        @Override
        public Table<?> table() {
            return PROCESS_INITIAL_STATE;
        }

        @Override
        public TableField<?, UUID> INSTANCE_ID() {
            return PROCESS_INITIAL_STATE.INSTANCE_ID;
        }

        @Override
        public TableField<?, OffsetDateTime> INSTANCE_CREATED_AT() {
            return PROCESS_INITIAL_STATE.INSTANCE_CREATED_AT;
        }

        @Override
        public TableField<?, String> ITEM_PATH() {
            return PROCESS_INITIAL_STATE.ITEM_PATH;
        }

        @Override
        public TableField<?, byte[]> ITEM_DATA() {
            return PROCESS_INITIAL_STATE.ITEM_DATA;
        }

        @Override
        public TableField<?, Short> UNIX_MODE() {
            return PROCESS_INITIAL_STATE.UNIX_MODE;
        }

        @Override
        public TableField<?, Boolean> IS_ENCRYPTED() {
            return PROCESS_INITIAL_STATE.IS_ENCRYPTED;
        }
    }

    static class CurrentProcessStateTable implements ProcessStateTable {

        public static CurrentProcessStateTable INSTANCE = new CurrentProcessStateTable();

        @Override
        public Table<?> table() {
            return PROCESS_STATE;
        }

        @Override
        public TableField<?, UUID> INSTANCE_ID() {
            return PROCESS_STATE.INSTANCE_ID;
        }

        @Override
        public TableField<?, OffsetDateTime> INSTANCE_CREATED_AT() {
            return PROCESS_STATE.INSTANCE_CREATED_AT;
        }

        @Override
        public TableField<?, String> ITEM_PATH() {
            return PROCESS_STATE.ITEM_PATH;
        }

        @Override
        public TableField<?, byte[]> ITEM_DATA() {
            return PROCESS_STATE.ITEM_DATA;
        }

        @Override
        public TableField<?, Short> UNIX_MODE() {
            return PROCESS_STATE.UNIX_MODE;
        }

        @Override
        public TableField<?, Boolean> IS_ENCRYPTED() {
            return PROCESS_STATE.IS_ENCRYPTED;
        }
    }
}
