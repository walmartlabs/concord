package com.walmartlabs.concord.server.security.ldap;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.security.ldap.LdapMappingEntry;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.tables.LdapGroupMappings.LDAP_GROUP_MAPPINGS;
import static com.walmartlabs.concord.server.jooq.tables.LdapGroupRoles.LDAP_GROUP_ROLES;

@Named
public class LdapDao extends AbstractDao {

    @Inject
    public LdapDao(Configuration cfg) {
        super(cfg);
    }

    public Collection<String> getRoles(Collection<String> ldapDns) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.selectDistinct(LDAP_GROUP_ROLES.ROLE_NAME)
                    .from(LDAP_GROUP_MAPPINGS)
                    .leftOuterJoin(LDAP_GROUP_ROLES).on(LDAP_GROUP_ROLES.MAPPING_ID.eq(LDAP_GROUP_MAPPINGS.MAPPING_ID))
                    .where(LDAP_GROUP_MAPPINGS.LDAP_DN.in(ldapDns))
                    .fetch(LDAP_GROUP_ROLES.ROLE_NAME);
        }
    }

    public boolean exists(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.fetchExists(tx.selectFrom(LDAP_GROUP_MAPPINGS)
                    .where(LDAP_GROUP_MAPPINGS.MAPPING_ID.eq(id)));
        }
    }

    public UUID getId(String ldapDn) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(LDAP_GROUP_MAPPINGS.MAPPING_ID)
                    .from(LDAP_GROUP_MAPPINGS)
                    .where(LDAP_GROUP_MAPPINGS.LDAP_DN.eq(ldapDn))
                    .fetchOne(LDAP_GROUP_MAPPINGS.MAPPING_ID);
        }
    }

    public void insert(UUID id, String ldapDn, Collection<String> roles) {
        tx(tx -> insert(tx, id, ldapDn, roles));
    }

    public void insert(DSLContext tx, UUID id, String ldapDn, Collection<String> roles) {
        tx.insertInto(LDAP_GROUP_MAPPINGS)
                .columns(LDAP_GROUP_MAPPINGS.MAPPING_ID, LDAP_GROUP_MAPPINGS.LDAP_DN)
                .values(id, ldapDn)
                .execute();

        insertRoles(tx, id, roles);
    }

    public void update(UUID id, String ldapDn, Collection<String> roles) {
        tx(tx -> update(tx, id, ldapDn, roles));
    }

    public void update(DSLContext tx, UUID id, String ldapDn, Collection<String> roles) {
        tx.update(LDAP_GROUP_MAPPINGS)
                .set(LDAP_GROUP_MAPPINGS.LDAP_DN, ldapDn)
                .where(LDAP_GROUP_MAPPINGS.MAPPING_ID.eq(id))
                .execute();

        deleteRoles(tx, id);
        insertRoles(tx, id, roles);
    }

    public List<LdapMappingEntry> list() {
        try (DSLContext tx = DSL.using(cfg)) {
            Select<Record3<UUID, String, String>> select = tx
                    .select(LDAP_GROUP_MAPPINGS.MAPPING_ID, LDAP_GROUP_MAPPINGS.LDAP_DN, LDAP_GROUP_ROLES.ROLE_NAME)
                    .from(LDAP_GROUP_MAPPINGS)
                    .leftOuterJoin(LDAP_GROUP_ROLES).on(LDAP_GROUP_ROLES.MAPPING_ID.eq(LDAP_GROUP_MAPPINGS.MAPPING_ID))
                    .orderBy(LDAP_GROUP_MAPPINGS.LDAP_DN);

            List<LdapMappingEntry> l = new ArrayList<>();

            UUID cId = null;
            String cDn = null;
            Set<String> cRoles = null;

            for (Record3<UUID, String, String> r : select.fetch()) {
                UUID id = r.get(LDAP_GROUP_MAPPINGS.MAPPING_ID);
                String dn = r.get(LDAP_GROUP_MAPPINGS.LDAP_DN);
                String perm = r.get(LDAP_GROUP_ROLES.ROLE_NAME);

                if (!id.equals(cId)) {
                    if (cId != null) {
                        l.add(new LdapMappingEntry(cId, cDn, cRoles));
                    }

                    cId = id;
                    cDn = dn;
                    cRoles = new HashSet<>();
                }

                if (perm != null) {
                    cRoles.add(perm);
                }
            }
            l.add(new LdapMappingEntry(cId, cDn, cRoles));

            return l;
        }
    }

    public void delete(UUID id) {
        tx(tx -> delete(tx, id));
    }

    public void delete(DSLContext tx, UUID id) {
        tx.deleteFrom(LDAP_GROUP_MAPPINGS)
                .where(LDAP_GROUP_MAPPINGS.MAPPING_ID.eq(id))
                .execute();
    }

    private static void deleteRoles(DSLContext tx, UUID mappingId) {
        tx.deleteFrom(LDAP_GROUP_ROLES)
                .where(LDAP_GROUP_ROLES.MAPPING_ID.eq(mappingId))
                .execute();
    }

    private static void insertRoles(DSLContext tx, UUID mappingId, Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return;
        }

        BatchBindStep b = tx.batch(tx.insertInto(LDAP_GROUP_ROLES)
                .columns(LDAP_GROUP_ROLES.MAPPING_ID, LDAP_GROUP_ROLES.ROLE_NAME)
                .values((UUID) null, null));

        for (String r : roles) {
            b.bind(mappingId, r);
        }

        b.execute();
    }
}
