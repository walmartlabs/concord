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


import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessKvResource;
import com.walmartlabs.concord.server.org.project.KvDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Named
public class ProcessKvResourceImpl implements ProcessKvResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessKvResource.class);

    private static final UUID DEFAULT_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final ProcessQueueDao queueDao;
    private final KvDao kvDao;

    @Inject
    public ProcessKvResourceImpl(ProcessQueueDao queueDao, KvDao kvDao) {
        this.queueDao = queueDao;
        this.kvDao = kvDao;
    }

    @Override
    public void removeKey(String instanceId, String key) {
        UUID projectId = assertProjectId(instanceId);

        kvDao.remove(projectId, key);
    }

    @Override
    public void putString(String instanceId, String key, String value) {
        UUID projectId = assertProjectId(instanceId);

        kvDao.putString(projectId, key, value);
    }

    @Override
    public String getString(String instanceId, String key) {
        UUID projectId = assertProjectId(instanceId);

        return kvDao.getString(projectId, key);
    }

    @Override
    public void putLong(String instanceId, String key, long value) {
        UUID projectId = assertProjectId(instanceId);

        kvDao.putLong(projectId, key, value);
    }

    @Override
    public Long getLong(String instanceId, String key) {
        UUID projectId = assertProjectId(instanceId);

        return kvDao.getLong(projectId, key);
    }

    @Override
    public long incLong(String instanceId, String key) {
        UUID projectId = assertProjectId(instanceId);

        return kvDao.inc(projectId, key);
    }

    private UUID assertProjectId(String instanceId) {
        ProcessEntry entry = queueDao.get(UUID.fromString(instanceId));
        if (entry == null) {
            throw new WebApplicationException("Process instance not found", Response.Status.NOT_FOUND);
        }

        UUID projectId = entry.getProjectId();
        if (projectId == null) {
            log.warn("assertProjectId ['{}'] -> no project found, using the default value", instanceId);
            projectId = DEFAULT_PROJECT_ID;
        }

        return projectId;
    }
}
