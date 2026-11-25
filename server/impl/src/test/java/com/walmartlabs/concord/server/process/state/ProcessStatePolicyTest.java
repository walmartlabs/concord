package com.walmartlabs.concord.server.process.state;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2026 Walmart Inc.
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

import com.walmartlabs.concord.server.cfg.ProcessConfiguration;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.POLICY_FILE_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessStatePolicyTest {

    @TempDir
    Path tempDir;

    @Test
    void excludesLegacyPolicyFromWorkspaceAndCheckpointStateImports() throws Exception {
        Files.createDirectories(tempDir.resolve(".concord"));
        Files.writeString(tempDir.resolve(POLICY_FILE_PATH), "old-policy");
        Files.writeString(tempDir.resolve(".concord/state"), "state");

        assertEquals(List.of(".concord/state"), importFiles(null, tempDir));
    }

    @Test
    void excludesPolicyWhenImportingSystemDirectoryWithPrefix() throws Exception {
        Files.writeString(tempDir.resolve("policy.json"), "old-policy");
        Files.writeString(tempDir.resolve("state"), "state");

        assertEquals(List.of(".concord/state"), importFiles(".concord", tempDir));
    }

    @Test
    void preservesUnrelatedPolicyFiles() throws Exception {
        Files.writeString(tempDir.resolve("policy.json"), "user-file");

        assertEquals(List.of("_attachments/policy.json"), importFiles("_attachments", tempDir));
    }

    private static List<String> importFiles(String prefix, Path src) throws Exception {
        List<String> inserted = new ArrayList<>();
        try (MockConnection connection = spy(new MockConnection(ctx -> {
            if (ctx.sql().startsWith("select")) {
                return new MockResult[]{new MockResult(0, DSL.using(SQLDialect.POSTGRES)
                        .newResult(DSL.field("orgId", UUID.class), DSL.field("prjId", UUID.class), DSL.field("userId", UUID.class)))};
            }
            return new MockResult[]{new MockResult(1)};
        }))) {
            PreparedStatement insert = mock(PreparedStatement.class);
            doAnswer(invocation -> {
                inserted.add(invocation.getArgument(1));
                return null;
            }).when(insert).setString(eq(3), anyString());
            doAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                return sql.startsWith("insert into") ? insert : invocation.callRealMethod();
            }).when(connection).prepareStatement(anyString());
            DSLContext dsl = DSL.using(connection, SQLDialect.POSTGRES);
            ProcessStateManager manager = new ProcessStateManager(dsl.configuration(), null,
                    new ProcessConfiguration(null, List.of()), null, null, null);
            manager.importPath(dsl, ProcessKey.random(), prefix, src, (file, attrs) -> true);
        }
        return inserted;
    }
}
