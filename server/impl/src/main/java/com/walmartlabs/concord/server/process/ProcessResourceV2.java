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

import com.walmartlabs.concord.server.IsoDateParam;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.process.queue.ProcessFilter;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.security.Permission;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserDao;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Named
@Singleton
@Api(value = "ProcessV2", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v2/process")
public class ProcessResourceV2 implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessResourceV2.class);

    private final ProcessQueueDao queueDao;
    private final ProjectDao projectDao;
    private final UserDao userDao;
    private final OrganizationManager orgManager;
    private final ProjectAccessManager projectAccessManager;

    @Inject
    public ProcessResourceV2(ProcessQueueDao queueDao,
                             ProjectDao projectDao,
                             UserDao userDao,
                             OrganizationManager orgManager,
                             ProjectAccessManager projectAccessManager) {

        this.queueDao = queueDao;
        this.projectDao = projectDao;
        this.userDao = userDao;
        this.orgManager = orgManager;
        this.projectAccessManager = projectAccessManager;
    }

    /**
     * Returns a process instance's details.
     */
    @GET
    @ApiOperation("Get a process' details")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public ProcessEntry get(@ApiParam @PathParam("id") UUID instanceId,
                            @ApiParam @QueryParam("include") Set<ProcessDataInclude> includes) {

        PartialProcessKey processKey = PartialProcessKey.from(instanceId);

        ProcessEntry e = queueDao.get(processKey, includes);
        if (e == null) {
            log.warn("get ['{}'] -> not found", instanceId);
            throw new ConcordApplicationException("Process instance not found", Status.NOT_FOUND);
        }

        if (e.projectId() != null) {
            projectAccessManager.assertProjectAccess(e.orgId(), e.projectId(), null, ResourceAccessLevel.READER, false);
        }

        return e;
    }

    /**
     * Returns a list of processes in the user's organizations.
     */
    @GET
    @ApiOperation(value = "List processes", responseContainer = "list", response = ProcessEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<ProcessEntry> list(@ApiParam @QueryParam("orgId") UUID orgId,
                                   @ApiParam @QueryParam("orgName") String orgName,
                                   @ApiParam @QueryParam("projectId") UUID projectId,
                                   @ApiParam @QueryParam("projectName") String projectName,
                                   @ApiParam @QueryParam("afterCreatedAt") IsoDateParam afterCreatedAt,
                                   @ApiParam @QueryParam("beforeCreatedAt") IsoDateParam beforeCreatedAt,
                                   @ApiParam @QueryParam("tags") Set<String> tags,
                                   @ApiParam @QueryParam("status") ProcessStatus processStatus,
                                   @ApiParam @QueryParam("initiator") String initiator,
                                   @ApiParam @QueryParam("parentInstanceId") UUID parentId,
                                   @ApiParam @QueryParam("include") Set<ProcessDataInclude> processData,
                                   @ApiParam @QueryParam("limit") @DefaultValue("30") int limit,
                                   @ApiParam @QueryParam("offset") @DefaultValue("0") int offset,
                                   @Context UriInfo uriInfo) {

        if (limit <= 0) {
            throw new ValidationErrorsException("'limit' must be a positive number");
        }

        UUID effectiveOrgId = orgId;

        Set<UUID> orgIds = null;
        if (orgId != null) {
            // we got an org ID, use it as it is
            orgIds = Collections.singleton(effectiveOrgId);
        } if (orgName != null) {
            // we got an org name, validate it first by resolving its ID
            OrganizationEntry org = orgManager.assertExisting(null, orgName);
            effectiveOrgId = org.getId();
            orgIds = Collections.singleton(effectiveOrgId);
        } else {
            // we got a query that is not limited to any specific org
            // let's check if we can return all processes from all orgs or if we should limit it to the user's orgs
            boolean canSeeAllOrgs = Roles.isAdmin() || Permission.isPermitted(Permission.GET_PROCESS_QUEUE_ALL_ORGS);
            if (!canSeeAllOrgs) {
                // non-admin users can only see their org's processes or processes w/o projects
                orgIds = getCurrentUserOrgIds();
            }
        }

        UUID effectiveProjectId = projectId;
        if (effectiveProjectId == null && projectName != null) {
            if (effectiveOrgId == null) {
                throw new ValidationErrorsException("Organization name or ID is required");
            }

            effectiveProjectId = projectDao.getId(effectiveOrgId, projectName);
            if (effectiveProjectId == null) {
                throw new ConcordApplicationException("Project not found: " + projectName, Response.Status.NOT_FOUND);
            }
        }

        if (effectiveProjectId != null) {
            projectAccessManager.assertProjectAccess(effectiveProjectId, effectiveProjectId, null, ResourceAccessLevel.READER, false);
        } else if (effectiveOrgId != null) {
            orgManager.assertAccess(effectiveOrgId, null, false);
        } else {
            // we don't have to do the permissions check when neither the org or the project are specified
            // it is done implicitly by calling getCurrentUserOrgIds for all non-adming users (see above)
        }

        // collect all metadata filters, we assume that they have "meta." prefix in ther query parameter names
        Map<String, String> metaFilters = uriInfo.getQueryParameters().entrySet().stream()
                .filter(e -> e.getKey().startsWith("meta."))
                .filter(e -> e.getValue() != null && e.getValue().size() == 1)
                .collect(Collectors.toMap(e -> e.getKey().substring("meta.".length()), e -> e.getValue().get(0)));

        ProcessFilter filter = ProcessFilter.builder()
                .parentId(parentId)
                .projectId(effectiveProjectId)
                .orgIds(orgIds)
                .includeWithoutProject(effectiveOrgId == null && effectiveProjectId == null)
                .afterCreatedAt(toTimestamp(afterCreatedAt))
                .beforeCreatedAt(toTimestamp(beforeCreatedAt))
                .tags(tags)
                .status(processStatus)
                .initiator(initiator)
                .metaFilters(metaFilters)
                .includes(processData)
                .build();

        return queueDao.list(filter, limit, offset);
    }

    private Set<UUID> getCurrentUserOrgIds() {
        UserPrincipal p = UserPrincipal.assertCurrent();
        return userDao.getOrgIds(p.getId());
    }

    private static Timestamp toTimestamp(IsoDateParam p) {
        if (p == null) {
            return null;
        }

        Calendar c = p.getValue();
        return new Timestamp(c.getTimeInMillis());
    }
}
