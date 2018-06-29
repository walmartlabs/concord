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

import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ProjectProcessResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static javax.ws.rs.core.Response.Status;

/**
 * @deprecated prefer {@link ProjectProcessResource}
 */
@Named
@Singleton
@Path("/api/service/process_portal")
@Deprecated
public class ProcessPortalService implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessPortalService.class);

    private final ProjectProcessResource processPortalResource;
    private final ResponseTemplates responseTemplates;

    @Inject
    public ProcessPortalService(ProjectProcessResource processPortalResource, ResponseTemplates responseTemplates) {
        this.processPortalResource = processPortalResource;
        this.responseTemplates = responseTemplates;
    }

    @GET
    @Path("/start")
    @Validate
    public Response startProcess(@QueryParam("entryPoint") String entryPoint,
                                 @QueryParam("activeProfiles") String activeProfiles,
                                 @Context UriInfo uriInfo) {

        if (entryPoint == null || entryPoint.trim().isEmpty()) {
            return badRequest("Entry point is not specified");
        }

        try {
            EntryPoint ep = parseEntryPoint(entryPoint);

            return processPortalResource.start(OrganizationManager.DEFAULT_ORG_NAME, ep.projectName, ep.repoName, ep.flow, activeProfiles, uriInfo);
        } catch (Exception e) {
            log.error("startProcess ['{}', '{}'] -> error", entryPoint, activeProfiles, e);
            return processError("Process error: " + e.getMessage());
        }
    }

    private EntryPoint parseEntryPoint(String entryPoint) {
        String[] as = entryPoint.split(":");
        if (as.length < 1 || as.length > 3) {
            throw new ValidationErrorsException("Invalid entry point format: " + entryPoint);
        }

        String projectName = as[0].trim();

        String repoName = null;
        if (as.length > 1) {
            repoName = as[1].trim();

        }

        String flow = null;
        if (as.length > 2) {
            flow = as[2].trim();
        }

        return new EntryPoint(projectName, repoName, flow);
    }

    private Response badRequest(String message) {
        return responseTemplates.badRequest(Response.status(Status.BAD_REQUEST),
                Collections.singletonMap("message", message))
                .build();
    }

    private Response processError(String message) {
        Map<String, Object> args = new HashMap<>();
        args.put("message", message);

        return responseTemplates.processError(Response.status(Status.INTERNAL_SERVER_ERROR), args)
                .build();
    }

    private static class EntryPoint {

        private final String projectName;
        private final String repoName;
        private final String flow;

        EntryPoint(String projectName, String repoName, String flow) {
            this.projectName = projectName;
            this.repoName = repoName;
            this.flow = flow;
        }
    }
}
