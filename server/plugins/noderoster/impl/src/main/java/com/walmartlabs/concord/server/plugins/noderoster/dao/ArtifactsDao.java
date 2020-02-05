package com.walmartlabs.concord.server.plugins.noderoster.dao;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.plugins.noderoster.ArtifactEntry;
import com.walmartlabs.concord.server.plugins.noderoster.db.NodeRosterDB;
import com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterHostArtifacts;
import org.jooq.Configuration;
import org.jooq.Record2;
import org.jooq.SelectJoinStep;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterHostArtifacts.NODE_ROSTER_HOST_ARTIFACTS;

@Named
@Singleton
public class ArtifactsDao extends AbstractDao {

    @Inject
    public ArtifactsDao(@NodeRosterDB Configuration cfg) {
        super(cfg);
    }

    public List<ArtifactEntry> getArtifacts(UUID hostId, int limit, int offset, String filter) {
        return txResult(tx -> {
            NodeRosterHostArtifacts a = NODE_ROSTER_HOST_ARTIFACTS.as("a");

            SelectJoinStep<Record2<String, UUID>> q = tx.select(a.ARTIFACT_URL, a.INSTANCE_ID)
                    .from(a);

            if (filter != null) {
                q.where(a.ARTIFACT_URL.likeRegex(filter));
            }

            return q.where(a.HOST_ID.eq(hostId))
                    .orderBy(a.INSTANCE_CREATED_AT.desc())
                    .limit(limit)
                    .offset(offset)
                    .fetch(r -> ArtifactEntry.of(r.get(a.ARTIFACT_URL), r.get(a.INSTANCE_ID)));
        });
    }
}
