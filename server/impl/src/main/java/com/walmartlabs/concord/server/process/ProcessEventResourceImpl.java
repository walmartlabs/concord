package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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


import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.api.IsoDateParam;
import com.walmartlabs.concord.server.api.process.ProcessEventEntry;
import com.walmartlabs.concord.server.api.process.ProcessEventRequest;
import com.walmartlabs.concord.server.api.process.ProcessEventResource;
import com.walmartlabs.concord.server.process.event.EventDao;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Named
public class ProcessEventResourceImpl implements ProcessEventResource, Resource {

    private final EventDao eventDao;
    private final ObjectMapper objectMapper;

    @Inject
    public ProcessEventResourceImpl(EventDao eventDao) {
        this.eventDao = eventDao;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void event(UUID processInstanceId, ProcessEventRequest req) {
        String data;
        try {
            data = objectMapper.writeValueAsString(req.getData());
        } catch (IOException e) {
            throw new WebApplicationException("Error while serializing the event's data: " + e.getMessage(), e);
        }

        eventDao.insert(processInstanceId, req.getEventType(), data);
    }

    @Override
    public List<ProcessEventEntry> list(UUID processInstanceId, IsoDateParam geTimestamp, int limit) {
        Timestamp ts = null;
        if (geTimestamp != null) {
            ts = Timestamp.from(geTimestamp.getValue().toInstant());
        }
        return eventDao.list(processInstanceId, ts, limit);
    }
}
