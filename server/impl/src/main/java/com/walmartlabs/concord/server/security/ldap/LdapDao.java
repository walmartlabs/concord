package com.walmartlabs.concord.server.security.ldap;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.security.ldap.LdapMappingEntry;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.public_.tables.LdapGroupMappings.LDAP_GROUP_MAPPINGS;
import static com.walmartlabs.concord.server.jooq.public_.tables.LdapGroupRoles.LDAP_GROUP_ROLES;

@Named
public class LdapDao extends AbstractDao {

    @Inject
    public LdapDao(Configuration cfg) {
        super(cfg);
    }

    public Collection<String> getRoles(Collection<String> ldapDns) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.selectDistinct(LDAP_GROUP_ROLES.ROLE_NAME)
                    .from(LDAP_GROUP_MAPPINGS)
                    .leftOuterJoin(LDAP_GROUP_ROLES).on(LDAP_GROUP_ROLES.MAPPING_ID.eq(LDAP_GROUP_MAPPINGS.MAPPING_ID))
                    .where(LDAP_GROUP_MAPPINGS.LDAP_DN.in(ldapDns))
                    .fetch(LDAP_GROUP_ROLES.ROLE_NAME);
        }
    }

    public boolean exists(String id) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.fetchExists(create.selectFrom(LDAP_GROUP_MAPPINGS)
                    .where(LDAP_GROUP_MAPPINGS.MAPPING_ID.eq(id)));
        }
    }

    public String getId(String ldapDn) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.select(LDAP_GROUP_MAPPINGS.MAPPING_ID)
                    .from(LDAP_GROUP_MAPPINGS)
                    .where(LDAP_GROUP_MAPPINGS.LDAP_DN.eq(ldapDn))
                    .fetchOne(LDAP_GROUP_MAPPINGS.MAPPING_ID);
        }
    }

    public void insert(String id, String ldapDn, Collection<String> roles) {
        tx(tx -> insert(tx, id, ldapDn, roles));
    }

    public void insert(DSLContext create, String id, String ldapDn, Collection<String> roles) {
        create.insertInto(LDAP_GROUP_MAPPINGS)
                .columns(LDAP_GROUP_MAPPINGS.MAPPING_ID, LDAP_GROUP_MAPPINGS.LDAP_DN)
                .values(id, ldapDn)
                .execute();

        insertRoles(create, id, roles);
    }

    public void update(String id, String ldapDn, Collection<String> roles) {
        tx(tx -> update(tx, id, ldapDn, roles));
    }

    public void update(DSLContext create, String id, String ldapDn, Collection<String> roles) {
        create.update(LDAP_GROUP_MAPPINGS)
                .set(LDAP_GROUP_MAPPINGS.LDAP_DN, ldapDn)
                .where(LDAP_GROUP_MAPPINGS.MAPPING_ID.eq(id))
                .execute();

        deleteRoles(create, id);
        insertRoles(create, id, roles);
    }

    public List<LdapMappingEntry> list() {
        try (DSLContext create = DSL.using(cfg)) {
            Select<Record3<String, String, String>> select = create
                    .select(LDAP_GROUP_MAPPINGS.MAPPING_ID, LDAP_GROUP_MAPPINGS.LDAP_DN, LDAP_GROUP_ROLES.ROLE_NAME)
                    .from(LDAP_GROUP_MAPPINGS)
                    .leftOuterJoin(LDAP_GROUP_ROLES).on(LDAP_GROUP_ROLES.MAPPING_ID.eq(LDAP_GROUP_MAPPINGS.MAPPING_ID))
                    .orderBy(LDAP_GROUP_MAPPINGS.LDAP_DN);

            List<LdapMappingEntry> l = new ArrayList<>();

            String cId = null;
            String cDn = null;
            Set<String> cRoles = null;

            for (Record3<String, String, String> r : select.fetch()) {
                String id = r.get(LDAP_GROUP_MAPPINGS.MAPPING_ID);
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

    public void delete(String id) {
        tx(tx -> delete(tx, id));
    }

    public void delete(DSLContext create, String id) {
        create.deleteFrom(LDAP_GROUP_MAPPINGS)
                .where(LDAP_GROUP_MAPPINGS.MAPPING_ID.eq(id))
                .execute();
    }

    private static void deleteRoles(DSLContext create, String mappingId) {
        create.deleteFrom(LDAP_GROUP_ROLES)
                .where(LDAP_GROUP_MAPPINGS.MAPPING_ID.eq(mappingId))
                .execute();
    }

    private static void insertRoles(DSLContext create, String mappingId, Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return;
        }

        BatchBindStep b = create.batch(create.insertInto(LDAP_GROUP_ROLES)
                .columns(LDAP_GROUP_ROLES.MAPPING_ID, LDAP_GROUP_ROLES.ROLE_NAME)
                .values((String) null, null));

        for (String r : roles) {
            b.bind(mappingId, r);
        }

        b.execute();
    }
}
