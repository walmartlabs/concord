package com.walmartlabs.concord.server.org.team;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.server.UuidGenerator;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TeamDaoProjectionTest {
    @Test
    void projectsDirectLdapAndOtherSourceContributions() {
        UUID user = UUID.randomUUID();
        DSLContext data = DSL.using(SQLDialect.POSTGRES);
        Field<?>[] fields = { DSL.field("user_id", UUID.class), DSL.field("username", String.class),
                DSL.field("domain", String.class), DSL.field("display_name", String.class),
                DSL.field("user_type", String.class), DSL.field("team_role", String.class),
                DSL.field("source_id", String.class), DSL.field("group_id", String.class) };
        Result<Record> direct = data.newResult(java.util.Arrays.copyOf(fields, 6));
        Record d = data.newRecord(java.util.Arrays.copyOf(fields, 6));
        d.fromArray(user, "alice", null, "Alice", null, "MEMBER");
        direct.add(d);
        Result<Record> source = data.newResult(fields);
        Record ldap = data.newRecord(fields);
        ldap.fromArray(user, "alice", null, "Alice", null, "MAINTAINER", "ldap", "staff");
        source.add(ldap);
        Record oidc = data.newRecord(fields);
        oidc.fromArray(user, "alice", null, "Alice", null, "OWNER", "oidc", null);
        source.add(oidc);
        MockConnection connection = new MockConnection(ctx -> {
            if (ctx.sql().contains("v_effective_source_groups")) {
                assertTrue(ctx.sql().contains("user_source_teams"));
                assertTrue(ctx.sql().contains("v_eligible_authorization_sources"));
                return new MockResult[] { new MockResult(source.size(), source) };
            }
            return new MockResult[] { new MockResult(direct.size(), direct) };
        });
        TeamDao dao = new TeamDao(DSL.using(connection, SQLDialect.POSTGRES).configuration(), mock(UuidGenerator.class));
        List<TeamUserEntry> entries = dao.listUsers(UUID.randomUUID());
        assertEquals(3, entries.size());
        assertEquals(TeamUserEntry.MembershipKind.DIRECT, entries.get(0).getMembershipKind());
        assertEquals(TeamMemberType.SINGLE, entries.get(0).getMemberType());
        assertEquals(TeamUserEntry.MembershipKind.SOURCE, entries.get(1).getMembershipKind());
        assertEquals(TeamMemberType.LDAP_GROUP, entries.get(1).getMemberType());
        assertEquals("staff", entries.get(1).getLdapGroupSource());
        assertEquals(new TeamUserEntry.GrantSource("ldap", "staff", TeamRole.MAINTAINER), entries.get(1).getGrantSources().get(0));
        assertNull(entries.get(2).getMemberType());
        assertNull(entries.get(2).getLdapGroupSource());
        assertEquals(new TeamUserEntry.GrantSource("oidc", null, TeamRole.OWNER), entries.get(2).getGrantSources().get(0));
    }
}
