package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.common.DateTimeUtils;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.UUID;

@Disabled
public class OffsetDateTimeTest extends AbstractDaoTest {

    public OffsetDateTimeTest() {
        super(false);
    }

    @Test
    public void test() throws Exception {
        UUID instanceId = UUID.fromString("c686b84f-3d0d-4958-a156-9257db3efa13");
        OffsetDateTime createdAt = DateTimeUtils.fromIsoString("2020-07-16T21:30:53.907Z");

        DSL.using(getConfiguration()).connection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("select * from process_state where instance_id = ? and instance_created_at = ?")) {
                ps.setObject(1, instanceId);
                ps.setObject(2, createdAt);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String s = rs.getString("item_path");
                        System.out.println(s);
                    }
                }
            }
        });
    }
}
