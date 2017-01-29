package com.walmartlabs.concord.plugins.ansible.inventory;

import com.walmartlabs.concord.bootstrap.db.AbstractDao;
import com.walmartlabs.concord.common.db.ResultSetInputStream;
import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.JDBCUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.InputStream;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.plugins.ansible.inventory.jooq.public_.tables.AnsibleInventories.ANSIBLE_INVENTORIES;

@Named
public class InventoryDao extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(InventoryDao.class);

    @Inject
    protected InventoryDao(Configuration cfg) {
        super(cfg);
    }

    public InventoryRecord get(String id) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.select(ANSIBLE_INVENTORIES.INVENTORY_ID, ANSIBLE_INVENTORIES.INVENTORY_NAME)
                    .from(ANSIBLE_INVENTORIES)
                    .where(ANSIBLE_INVENTORIES.INVENTORY_ID.eq(id))
                    .fetchOne(r -> new InventoryRecord(
                            r.get(ANSIBLE_INVENTORIES.INVENTORY_ID),
                            r.get(ANSIBLE_INVENTORIES.INVENTORY_NAME)));
        }
    }

    public String getId(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.select(ANSIBLE_INVENTORIES.INVENTORY_ID)
                    .from(ANSIBLE_INVENTORIES)
                    .where(ANSIBLE_INVENTORIES.INVENTORY_NAME.eq(name))
                    .fetchOne(ANSIBLE_INVENTORIES.INVENTORY_ID);
        }
    }

    public InputStream getData(String id) {
        String sql;
        try (DSLContext create = DSL.using(cfg)) {
            sql = create.select(ANSIBLE_INVENTORIES.INVENTORY_DATA)
                    .from(ANSIBLE_INVENTORIES)
                    .where(ANSIBLE_INVENTORIES.INVENTORY_ID.eq(id))
                    .getSQL();
        }

        Connection conn = cfg.connectionProvider().acquire();

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(sql);
            ps.setString(1, id);

            InputStream in = ResultSetInputStream.open(conn, ps, 1);
            if (in == null) {
                JDBCUtils.safeClose(ps);
                JDBCUtils.safeClose(conn);
                return null;
            }
            return in;
        } catch (SQLException e) {
            JDBCUtils.safeClose(ps);
            JDBCUtils.safeClose(conn);
            throw new DataAccessException("Error while opening a stream", e);
        }
    }

    public void insert(String id, String name, InputStream data) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);
            String sql = create.insertInto(ANSIBLE_INVENTORIES)
                    .columns(ANSIBLE_INVENTORIES.INVENTORY_ID, ANSIBLE_INVENTORIES.INVENTORY_NAME, ANSIBLE_INVENTORIES.INVENTORY_DATA)
                    .values((String) null, null, null)
                    .getSQL();

            create.connection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, id);
                    ps.setString(2, name);
                    ps.setBinaryStream(3, data);
                    ps.executeUpdate();
                }
            });
        });
        log.info("insert ['{}', '{}'] -> done", id, name);
    }

    public void update(String id, InputStream data) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);
            String sql = create.update(ANSIBLE_INVENTORIES)
                    .set(ANSIBLE_INVENTORIES.INVENTORY_DATA, (byte[]) null)
                    .where(ANSIBLE_INVENTORIES.INVENTORY_ID.eq(id))
                    .getSQL();

            create.connection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setBinaryStream(1, data);
                    ps.setString(2, id);
                    ps.executeUpdate();
                }
            });
        });
        log.info("update ['{}'] -> done", id);
    }

    public void delete(String id) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);
            create.deleteFrom(ANSIBLE_INVENTORIES)
                    .where(ANSIBLE_INVENTORIES.INVENTORY_ID.eq(id))
                    .execute();
        });
        log.info("delete ['{}'] -> done", id);
    }

    public List<InventoryRecord> list(Field<?> sortField, boolean asc) {
        try (DSLContext create = DSL.using(cfg)) {
            SelectJoinStep<Record2<String, String>> query = create.select(
                    ANSIBLE_INVENTORIES.INVENTORY_ID,
                    ANSIBLE_INVENTORIES.INVENTORY_NAME)
                    .from(ANSIBLE_INVENTORIES);

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            List<InventoryRecord> result = query.stream().map(r -> new InventoryRecord(
                    r.get(ANSIBLE_INVENTORIES.INVENTORY_ID),
                    r.get(ANSIBLE_INVENTORIES.INVENTORY_NAME)))
                    .collect(Collectors.toList());

            log.info("list ['{}', {}] -> found {} item(s)", sortField, asc, result.size());
            return result;
        }
    }

    public static class InventoryRecord implements Serializable {

        private final String id;
        private final String name;

        public InventoryRecord(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
