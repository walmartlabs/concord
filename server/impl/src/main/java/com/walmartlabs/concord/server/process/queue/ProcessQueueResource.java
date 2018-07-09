package com.walmartlabs.concord.server.process.queue;

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
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.util.Map;

@Named
@Singleton
@Api(value = "Process Queue", authorizations = {@Authorization("api_key")})
@Path("/api/v1/process/queue")
public class ProcessQueueResource implements Resource {

    private final ProcessManager processManager;
    private final LogManager logManager;

    @Inject
    public ProcessQueueResource(ProcessManager processManager,
                                LogManager logManager) {

        this.processManager = processManager;
        this.logManager = logManager;
    }

    @POST
    @ApiOperation("Take a payload from the queue")
    @Path("/take")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ProcessEntry take(@ApiParam Map<String, Object> capabilities,
                             @Context HttpHeaders headers) {

        assertPermissions();

        ProcessEntry p = processManager.nextPayload(capabilities);
        if (p == null) {
            return null;
        }

        String userAgent = headers.getHeaderString(HttpHeaders.USER_AGENT);
        if (userAgent == null) {
            userAgent = "unknown";
        }
        logManager.info(p.getInstanceId(), "Acquired by: " + userAgent);

        return p;
    }

    private static void assertPermissions() {
        UserPrincipal p = UserPrincipal.getCurrent();
        if (p.isAdmin() || p.isGlobalReader()) {
            return;
        }

        throw new UnauthorizedException("Forbidden");
    }
}
