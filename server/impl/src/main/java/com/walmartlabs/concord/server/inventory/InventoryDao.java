package com.walmartlabs.concord.server.inventory;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.inventory.InventoryEntry;
import com.walmartlabs.concord.server.jooq.tables.Inventories;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Inventories.INVENTORIES;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static org.jooq.impl.DSL.*;

@Named
public class InventoryDao extends AbstractDao {

    @Inject
    public InventoryDao(Configuration cfg) {
        super(cfg);
    }

    public UUID getId(String inventoryName) {
        try(DSLContext tx = DSL.using(cfg)) {
            return tx.select(INVENTORIES.INVENTORY_ID)
                    .from(INVENTORIES)
                    .where(INVENTORIES.INVENTORY_NAME.eq(inventoryName))
                    .fetchOne(INVENTORIES.INVENTORY_ID);
        }
    }

    public UUID insert(String name, UUID teamId, UUID parentId) {
        return txResult(tx -> insert(tx, name, teamId, parentId));
    }

    public void update(UUID inventoryId, String inventoryName, UUID teamId, UUID parentId) {
        tx(tx -> update(tx, inventoryId, inventoryName, teamId, parentId));
    }

    public void delete(UUID inventoryId) {
        tx(tx -> delete(tx, inventoryId));
    }

    public InventoryEntry get(UUID inventoryId) {
        try (DSLContext tx = DSL.using(cfg)) {
            Table<Record> nodes = table("nodes");
            Inventories i1 = INVENTORIES.as("i1");
            Inventories i2 = INVENTORIES.as("i2");

            Field<String> teamNameField1 = select(TEAMS.TEAM_NAME).from(TEAMS).where(TEAMS.TEAM_ID.eq(i1.TEAM_ID)).asField();
            Field<String> teamNameField2 = select(TEAMS.TEAM_NAME).from(TEAMS).where(TEAMS.TEAM_ID.eq(i2.TEAM_ID)).asField();

            SelectConditionStep<Record5<UUID, String, UUID, UUID, String>> s1 =
                    select(i1.INVENTORY_ID, i1.INVENTORY_NAME, i1.PARENT_INVENTORY_ID, i1.TEAM_ID, teamNameField1)
                            .from(i1)
                            .where(i1.INVENTORY_ID.eq(inventoryId));

            SelectConditionStep<Record5<UUID, String, UUID, UUID, String>> s2 =
                    select(i2.INVENTORY_ID, i2.INVENTORY_NAME, i2.PARENT_INVENTORY_ID, i2.TEAM_ID, teamNameField2)
                            .from(i2, nodes)
                            .where(i2.INVENTORY_ID.eq(INVENTORIES.as("nodes").PARENT_INVENTORY_ID));

            List<InventoryEntity> items =
                    tx.withRecursive("nodes", INVENTORIES.INVENTORY_ID.getName(), INVENTORIES.INVENTORY_NAME.getName(), INVENTORIES.PARENT_INVENTORY_ID.getName(), INVENTORIES.TEAM_ID.getName(), TEAMS.TEAM_NAME.getName())
                    .as(s1.unionAll(s2))
                    .select().from(nodes)
                    .fetch(InventoryDao::toEntity);

            if (items.isEmpty()) {
                return null;
            }

            return buildEntity(inventoryId, items);
        }
    }

    private static InventoryEntry buildEntity(UUID inventoryId, List<InventoryEntity> items) {
        InventoryEntity i = items.stream()
                .filter(e -> e.getId().equals(inventoryId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Can't find inventory in results"));

        return new InventoryEntry(inventoryId, i.getName(), i.getTeamId(), i.getTeamName(),
                buildParent(i.getParentId(), items));
    }

    private static InventoryEntry buildParent(UUID parentId, List<InventoryEntity> items) {
        if (parentId == null) {
            return null;
        }

        InventoryEntity entity = items.stream()
                .filter(e -> e.getId().equals(parentId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Can't find parent inventory in results"));

        return new InventoryEntry(entity.getId(), entity.getName(),
                entity.getTeamId(), entity.getTeamName(),
                buildParent(entity.getParentId(), items));
    }

    private static InventoryEntity toEntity(Record r) {
        return new InventoryEntity(r.getValue(INVENTORIES.INVENTORY_ID),
                r.getValue(INVENTORIES.INVENTORY_NAME),
                r.getValue(INVENTORIES.TEAM_ID),
                r.getValue(TEAMS.TEAM_NAME),
                r.getValue(INVENTORIES.PARENT_INVENTORY_ID));
    }

    private UUID insert(DSLContext tx, String name, UUID teamId, UUID parentId) {
        return tx.insertInto(INVENTORIES)
                .columns(INVENTORIES.INVENTORY_NAME, INVENTORIES.TEAM_ID, INVENTORIES.PARENT_INVENTORY_ID)
                .values(value(name), value(teamId), value(parentId))
                .returning(INVENTORIES.INVENTORY_ID)
                .fetchOne()
                .getInventoryId();
    }

    private void update(DSLContext tx, UUID inventoryId, String inventoryName, UUID teamId, UUID parentId) {
        tx.update(INVENTORIES)
                .set(INVENTORIES.INVENTORY_NAME, value(inventoryName))
                .set(INVENTORIES.TEAM_ID, value(teamId))
                .set(INVENTORIES.PARENT_INVENTORY_ID, value(parentId))
                .where(INVENTORIES.INVENTORY_ID.eq(inventoryId))
                .execute();
    }

    private void delete(DSLContext tx, UUID inventoryId) {
        tx.deleteFrom(INVENTORIES)
                .where(INVENTORIES.INVENTORY_ID.eq(inventoryId))
                .execute();
    }

    private static class InventoryEntity {

        private final UUID id;

        private final String name;

        private final UUID teamId;

        private final String teamName;

        private final UUID parentId;

        public InventoryEntity(UUID id, String name, UUID teamId, String teamName, UUID parentId) {
            this.id = id;
            this.name = name;
            this.teamId = teamId;
            this.teamName = teamName;
            this.parentId = parentId;
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public UUID getTeamId() {
            return teamId;
        }

        public String getTeamName() {
            return teamName;
        }

        public UUID getParentId() {
            return parentId;
        }
    }
}
