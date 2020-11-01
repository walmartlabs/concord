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

import com.walmartlabs.concord.server.OffsetDateTimeParam;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.process.queue.*;
import com.walmartlabs.concord.server.process.queue.ProcessFilter.MetadataFilter;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.server.Utils.unwrap;

@Named
@Singleton
@Api(value = "ProcessV2", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v2/process")
public class ProcessResourceV2 implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessResourceV2.class);

    private final ProcessQueueDao queueDao;
    private final ProcessQueueManager processQueueManager;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final UserDao userDao;
    private final OrganizationManager orgManager;
    private final ProjectAccessManager projectAccessManager;

    @Inject
    public ProcessResourceV2(ProcessQueueDao queueDao,
                             ProcessQueueManager processQueueManager,
                             ProjectDao projectDao,
                             RepositoryDao repositoryDao,
                             UserDao userDao,
                             OrganizationManager orgManager,
                             ProjectAccessManager projectAccessManager) {

        this.queueDao = queueDao;
        this.processQueueManager = processQueueManager;
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
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

        ProcessEntry e = processQueueManager.get(processKey, includes);
        if (e == null) {
            log.warn("get ['{}'] -> not found", instanceId);
            throw new ConcordApplicationException("Process instance not found", Status.NOT_FOUND);
        }

        if (e.projectId() != null) {
            projectAccessManager.assertAccess(e.orgId(), e.projectId(), null, ResourceAccessLevel.READER, false);
        }

        return e;
    }

    /**
     * Returns a list of processes applying the specified filters.
     */
    @GET
    @ApiOperation(value = "List processes", responseContainer = "list", response = ProcessEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<ProcessEntry> list(@ApiParam @QueryParam("orgId") UUID orgId,
                                   @ApiParam @QueryParam("orgName") String orgName,
                                   @ApiParam @QueryParam("projectId") UUID projectId,
                                   @ApiParam @QueryParam("projectName") String projectName,
                                   @ApiParam @QueryParam("repoId") UUID repoId,
                                   @ApiParam @QueryParam("repoName") String repoName,
                                   @ApiParam @QueryParam("afterCreatedAt") OffsetDateTimeParam afterCreatedAt,
                                   @ApiParam @QueryParam("beforeCreatedAt") OffsetDateTimeParam beforeCreatedAt,
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

        if (offset < 0) {
            throw new ValidationErrorsException("'offset' must be a positive number or zero");
        }

        ProcessFilter filter = createProcessFilter(orgId, orgName, projectId, projectName, repoId, repoName,
                afterCreatedAt, beforeCreatedAt, tags, processStatus, initiator, parentId, processData, limit, offset, uriInfo);

        return queueDao.list(filter);
    }

    @GET
    @ApiOperation(value = "List process requirements")
    @Path("/requirements")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<ProcessRequirementsEntry> listRequirements(@ApiParam @QueryParam("status") ProcessStatus processStatus,
                                                           @ApiParam @QueryParam("limit") @DefaultValue("30") int limit,
                                                           @ApiParam @QueryParam("offset") @DefaultValue("0") int offset,
                                                           @Context UriInfo uriInfo) {

        if (limit <= 0) {
            throw new ValidationErrorsException("'limit' must be a positive number");
        }

        if (offset < 0) {
            throw new ValidationErrorsException("'offset' must be a positive number or zero");
        }

        if (processStatus == null) {
            throw new ValidationErrorsException("'status' is required");
        }

        return queueDao.listRequirements(processStatus, FilterUtils.parseDate("startAt", uriInfo), limit, offset,
                FilterUtils.parseJson("requirements", uriInfo));
    }

    /**
     * Counts processes applying the specified filters.
     */
    @GET
    @ApiOperation(value = "Count processes")
    @Path("/count")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public int count(@ApiParam @QueryParam("orgId") UUID orgId,
                     @ApiParam @QueryParam("orgName") String orgName,
                     @ApiParam @QueryParam("projectId") UUID projectId,
                     @ApiParam @QueryParam("projectName") String projectName,
                     @ApiParam @QueryParam("repoId") UUID repoId,
                     @ApiParam @QueryParam("repoName") String repoName,
                     @ApiParam @QueryParam("afterCreatedAt") OffsetDateTimeParam afterCreatedAt,
                     @ApiParam @QueryParam("beforeCreatedAt") OffsetDateTimeParam beforeCreatedAt,
                     @ApiParam @QueryParam("tags") Set<String> tags,
                     @ApiParam @QueryParam("status") ProcessStatus processStatus,
                     @ApiParam @QueryParam("initiator") String initiator,
                     @ApiParam @QueryParam("parentInstanceId") UUID parentId,
                     @Context UriInfo uriInfo) {

        ProcessFilter filter = createProcessFilter(orgId, orgName, projectId, projectName, repoId, repoName,
                afterCreatedAt, beforeCreatedAt, tags, processStatus, initiator, parentId, Collections.emptySet(),
                null, null, uriInfo);

        if (filter.projectId() == null) {
            throw new ValidationErrorsException("A project ID or name is required");
        }

        return queueDao.count(filter);
    }

    private ProcessFilter createProcessFilter(UUID orgId,
                                              String orgName,
                                              UUID projectId,
                                              String projectName,
                                              UUID repoId,
                                              String repoName,
                                              OffsetDateTimeParam afterCreatedAt,
                                              OffsetDateTimeParam beforeCreatedAt,
                                              Set<String> tags,
                                              ProcessStatus processStatus,
                                              String initiator,
                                              UUID parentId,
                                              Set<ProcessDataInclude> processData,
                                              Integer limit,
                                              Integer offset,
                                              UriInfo uriInfo) {

        UUID effectiveOrgId = orgId;

        Set<UUID> orgIds = null;
        if (orgId != null) {
            // we got an org ID, use it as it is
            orgIds = Collections.singleton(effectiveOrgId);
        }
        if (orgName != null) {
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
            projectAccessManager.assertAccess(effectiveProjectId, effectiveProjectId, null, ResourceAccessLevel.READER, false);
        } else if (effectiveOrgId != null) {
            orgManager.assertAccess(effectiveOrgId, null, false);
        } else {
            // we don't have to do the permissions check when neither the org or the project are specified
            // it is done implicitly by calling getCurrentUserOrgIds for all non-admin users (see above)
        }

        UUID effectiveRepoId = repoId;
        if (effectiveRepoId == null && repoName != null && effectiveProjectId != null) {
            effectiveRepoId = repositoryDao.getId(effectiveProjectId, repoName);
        }

        // collect all metadata filters, we assume that they have "meta." prefix in their query parameter names
        List<MetadataFilter> metaFilters = MetadataUtils.parseMetadataFilters(uriInfo);

        // can't allow seq scans, we don't index PROCESS_QUEUE.META (yet?)
        if (!metaFilters.isEmpty() && effectiveProjectId == null) {
            throw new ValidationErrorsException("Process metadata filters require a project name or an ID to be included in the query.");
        }

        return ProcessFilter.builder()
                .parentId(parentId)
                .projectId(effectiveProjectId)
                .orgIds(orgIds)
                .includeWithoutProject(effectiveOrgId == null && effectiveProjectId == null)
                .afterCreatedAt(unwrap(afterCreatedAt))
                .beforeCreatedAt(unwrap(beforeCreatedAt))
                .repoId(effectiveRepoId)
                .repoName(repoName)
                .tags(tags)
                .status(processStatus)
                .initiator(initiator)
                .metaFilters(metaFilters)
                .requirements(FilterUtils.parseJson("requirements", uriInfo))
                .startAt(FilterUtils.parseDate("startAt", uriInfo))
                .includes(processData != null ? processData : Collections.emptySet())
                .limit(limit)
                .offset(offset)
                .build();
    }

    private Set<UUID> getCurrentUserOrgIds() {
        UserPrincipal p = UserPrincipal.assertCurrent();
        return userDao.getOrgIds(p.getId());
    }
}
