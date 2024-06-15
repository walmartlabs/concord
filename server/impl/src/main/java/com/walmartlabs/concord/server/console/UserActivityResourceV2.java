package com.walmartlabs.concord.server.console;

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

import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.queue.ProcessFilter;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.UserPrincipal;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/v2/service/console/user")
public class UserActivityResourceV2 implements Resource {

    private final ProcessQueueDao processDao;

    @Inject
    public UserActivityResourceV2(ProcessQueueDao processDao) {
        this.processDao = processDao;
    }

    @GET
    @Path("/activity")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public UserActivityResponse activity(@QueryParam("maxOwnProcesses") @DefaultValue("5") int maxOwnProcesses) {

        UserPrincipal user = UserPrincipal.assertCurrent();

        ProcessFilter filter = ProcessFilter.builder()
                .initiator(user.getUsername())
                .includeWithoutProject(true)
                .limit(maxOwnProcesses)
                .build();
        List<ProcessEntry> lastProcesses = processDao.list(filter);

        return ImmutableUserActivityResponse.builder()
                .processes(lastProcesses)
                .build();
    }
}
