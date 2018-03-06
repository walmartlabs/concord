package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.server.api.IsoDateParam;
import com.walmartlabs.concord.server.api.process.ProcessEventEntry;
import com.walmartlabs.concord.server.api.process.ProcessEventResource;
import com.walmartlabs.concord.server.process.event.EventDao;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Named
public class ProcessEventResourceImpl implements ProcessEventResource, Resource {

    private final EventDao eventDao;

    @Inject
    public ProcessEventResourceImpl(EventDao eventDao) {
        this.eventDao = eventDao;
    }

    @Override
    public List<ProcessEventEntry> list(UUID processInstanceId, IsoDateParam afterTimestamp, int limit) {
        Timestamp ts = null;
        if (afterTimestamp != null) {
            ts = Timestamp.from(afterTimestamp.getValue().toInstant());
        }
        return eventDao.list(processInstanceId, ts, limit);
    }
}
