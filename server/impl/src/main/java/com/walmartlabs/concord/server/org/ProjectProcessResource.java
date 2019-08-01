package com.walmartlabs.concord.server.org;

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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.IsoDateParam;
import com.walmartlabs.concord.server.console.ResponseTemplates;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.process.form.ConcordFormService;
import com.walmartlabs.concord.server.process.pipelines.processors.RequestInfoProcessor;
import com.walmartlabs.concord.server.process.queue.ProcessFilter;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.security.UserPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.util.*;

import static javax.ws.rs.core.Response.Status;

@Named
@Singleton
@Api(value = "Project Processes", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public class ProjectProcessResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ProjectProcessResource.class);

    private static final String DEFAULT_LIST_LIMIT = "100";

    private final ProcessManager processManager;
    private final OrganizationManager orgManager;
    private final OrganizationDao orgDao;
    private final ProcessQueueDao queueDao;
    private final ConcordFormService formService;
    private final ResponseTemplates responseTemplates;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;

    @Inject
    public ProjectProcessResource(ProcessManager processManager,
                                  OrganizationDao orgDao,
                                  ProcessQueueDao queueDao,
                                  ConcordFormService formService,
                                  OrganizationManager orgManager,
                                  ProjectDao projectDao,
                                  RepositoryDao repositoryDao) {

        this.processManager = processManager;
        this.orgDao = orgDao;
        this.queueDao = queueDao;
        this.formService = formService;
        this.responseTemplates = new ResponseTemplates();
        this.orgManager = orgManager;
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
    }

    @GET
    @ApiOperation("List processes for the specified organization")
    @Path("/{orgName}/process")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Deprecated
    // TODO replace with /api/v1/process?orgName=...&status=...
    public List<ProcessEntry> list(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                   @ApiParam @QueryParam("status") ProcessStatus processStatus,
                                   @ApiParam @QueryParam("afterCreatedAt") IsoDateParam afterCreatedAt,
                                   @ApiParam @QueryParam("beforeCreatedAt") IsoDateParam beforeCreatedAt,
                                   @ApiParam @QueryParam("limit") @DefaultValue(DEFAULT_LIST_LIMIT) int limit,
                                   @ApiParam @QueryParam("offset") @DefaultValue("0") int offset) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        ProcessFilter filter = ProcessFilter.builder()
                .orgIds(Collections.singleton(org.getId()))
                .status(processStatus)
                .afterCreatedAt(toTimestamp(afterCreatedAt))
                .beforeCreatedAt(toTimestamp(beforeCreatedAt))
                .build();
        return queueDao.list(filter, limit, offset);
    }

    @GET
    @ApiOperation("List processes for the specified project")
    @Path("/{orgName}/project/{projectName}/process")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Deprecated
    public List<ProcessEntry> list(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                   @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                   @ApiParam @QueryParam("status") ProcessStatus processStatus,
                                   @ApiParam @QueryParam("afterCreatedAt") IsoDateParam afterCreatedAt,
                                   @ApiParam @QueryParam("beforeCreatedAt") IsoDateParam beforeCreatedAt,
                                   @ApiParam @QueryParam("limit") @DefaultValue(DEFAULT_LIST_LIMIT) int limit,
                                   @ApiParam @QueryParam("offset") @DefaultValue("0") int offset) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Response.Status.NOT_FOUND);
        }

        ProcessFilter filter = ProcessFilter.builder()
                .projectId(projectId)
                .status(processStatus)
                .afterCreatedAt(toTimestamp(afterCreatedAt))
                .beforeCreatedAt(toTimestamp(beforeCreatedAt))
                .build();
        return queueDao.list(filter, limit, offset);
    }

    /**
     * Starts a new process instance.
     *
     * @param orgName
     * @param projectName
     * @param repoName
     * @param entryPoint
     * @param activeProfiles
     * @return
     */
    @GET
    @ApiOperation("Start a new process")
    @Path("/{orgName}/project/{projectName}/repo/{repoName}/start/{entryPoint}")
    @Validate
    public Response start(@ApiParam @PathParam("orgName") String orgName,
                          @ApiParam @PathParam("projectName") String projectName,
                          @ApiParam @PathParam("repoName") String repoName,
                          @ApiParam @QueryParam("repoBranchOrTag") String repoBranchOrTag,
                          @ApiParam @QueryParam("repoCommitId") String repoCommitId,
                          @ApiParam @PathParam("entryPoint") String entryPoint,
                          @ApiParam @QueryParam("activeProfiles") String activeProfiles,
                          @Context HttpServletRequest request) {

        try {
            UUID orgId = getOrgId(orgName);
            UUID projectId = getProjectId(orgId, projectName);
            UUID repoId = getRepoId(projectId, repoName);

            Map<String, Object> requestInfo = parseRequestInfo(request);

            return doStartProcess(orgId, projectId, repoId, repoBranchOrTag, repoCommitId, entryPoint, activeProfiles, requestInfo);
        } catch (Exception e) {
            log.warn("startProcess ['{}', '{}', '{}', '{}', '{}'] -> error: {}", orgName, projectName, repoName, entryPoint, activeProfiles, e.getMessage());
            return processError(null, "Process error: " + e.getMessage(), e);
        }
    }

    private Response doStartProcess(UUID orgId,
                                    UUID projectId,
                                    UUID repoId,
                                    String branchOrTag,
                                    String commitId,
                                    String entryPoint,
                                    String activeProfiles,
                                    Map<String, Object> requestInfo) {

        Map<String, Object> cfg = new HashMap<>();

        if (branchOrTag != null) {
            cfg.put(Constants.Request.REPO_BRANCH_OR_TAG, branchOrTag);
        }

        if (commitId != null) {
            cfg.put(Constants.Request.REPO_COMMIT_ID, commitId);
        }

        if (activeProfiles != null) {
            String[] as = activeProfiles.split(",");
            cfg.put(InternalConstants.Request.ACTIVE_PROFILES_KEY, Arrays.asList(as));
        }

        if (requestInfo != null) {
            cfg.put(InternalConstants.Request.ARGUMENTS_KEY, requestInfo);
        }

        PartialProcessKey processKey = PartialProcessKey.create();

        try {
            UserPrincipal initiator = UserPrincipal.assertCurrent();

            Payload payload = PayloadBuilder.start(processKey)
                    .organization(orgId)
                    .project(projectId)
                    .repository(repoId)
                    .entryPoint(entryPoint)
                    .initiator(initiator.getId(), initiator.getUsername())
                    .configuration(cfg)
                    .build();

            processManager.start(payload);
        } catch (Exception e) {
            return processError(processKey, e.getMessage(), e);
        }

        return proceed(processKey);
    }

    private Map<String, Object> parseRequestInfo(HttpServletRequest request) {
        Map<String, Object> args = new HashMap<>();
        args.put("requestInfo", RequestInfoProcessor.getRequestInfo(request));
        args.putAll(parseArguments(request));
        return args;
    }

    @POST
    @ApiOperation("Proceed to next step for the process")
    @Path("{processInstanceId}/next")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response proceed(@PathParam("processInstanceId") UUID processInstanceId) {
        PartialProcessKey processKey = PartialProcessKey.from(processInstanceId);
        return proceed(processKey);
    }

    private Response proceed(PartialProcessKey processKey) {
        ProcessEntry entry = queueDao.get(processKey);
        if (entry == null) {
            throw new ConcordApplicationException("Process not found: " + processKey, Status.NOT_FOUND);
        }

        ProcessKey pk = ProcessKey.from(entry);

        ProcessStatus s = entry.status();
        if (s == ProcessStatus.FAILED || s == ProcessStatus.CANCELLED || s == ProcessStatus.TIMED_OUT) {
            return processError(processKey, "Process failed: " + s, null);
        } else if (s == ProcessStatus.FINISHED) {
            return processFinished(processKey);
        } else if (s == ProcessStatus.SUSPENDED) {
            String nextFormId = formService.nextFormId(pk);
            if (nextFormId == null) {
                return processError(processKey, "Invalid process state: no forms found", null);
            }

            String url = "/#/process/" + entry.instanceId() + "/wizard";
            return Response.status(Status.MOVED_PERMANENTLY)
                    .header(HttpHeaders.LOCATION, url)
                    .build();
        } else {
            Map<String, Object> args = prepareArgumentsForInProgressTemplate(entry);
            return responseTemplates.inProgressWait(Response.ok(), args).build();
        }
    }

    private static Timestamp toTimestamp(IsoDateParam p) {
        if (p == null) {
            return null;
        }

        Calendar c = p.getValue();
        return new Timestamp(c.getTimeInMillis());
    }

    private static Map<String, Object> prepareArgumentsForInProgressTemplate(ProcessEntry entry) {
        Map<String, Object> args = new HashMap<>();
        args.put("orgName", entry.orgName());
        args.put("projectName", entry.projectName());
        args.put("instanceId", entry.instanceId().toString());
        args.put("parentInstanceId", entry.parentInstanceId());
        args.put("initiator", entry.initiator());
        args.put("createdAt", entry.createdAt());
        args.put("lastUpdatedAt", entry.lastUpdatedAt());
        args.put("status", entry.status().toString());
        return args;
    }

    private static Map<String, Object> parseArguments(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();

        for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
            String k = e.getKey();
            if (!k.startsWith("arguments.")) {
                continue;
            }
            k = k.substring("arguments.".length());

            Object v = e.getValue();
            if (e.getValue().length == 1) {
                v = e.getValue()[0];
            }

            Map<String, Object> m = ConfigurationUtils.toNested(k, v);
            result = ConfigurationUtils.deepMerge(result, m);
        }

        return result;
    }

    private UUID getOrgId(String orgName) {
        UUID id = orgDao.getId(orgName);
        if (id == null) {
            throw new ValidationErrorsException("Organization not found: " + orgName);
        }
        return id;
    }

    private UUID getProjectId(UUID orgId, String projectName) {
        if (projectName == null) {
            return null;
        }

        if (orgId == null) {
            throw new ValidationErrorsException("Organization name is required");
        }

        UUID id = projectDao.getId(orgId, projectName);
        if (id == null) {
            throw new ValidationErrorsException("Project not found: " + projectName);
        }
        return id;
    }

    private UUID getRepoId(UUID projectId, String repoName) {
        if (repoName == null) {
            return null;
        }

        if (projectId == null) {
            throw new ValidationErrorsException("Project name is required");
        }

        UUID id = repositoryDao.getId(projectId, repoName);
        if (id == null) {
            throw new ValidationErrorsException("Repository not found: " + repoName);
        }
        return id;
    }

    private Response processFinished(PartialProcessKey processKey) {
        return responseTemplates.processFinished(Response.ok(),
                Collections.singletonMap("instanceId", processKey.getInstanceId()))
                .build();
    }

    private Response processError(PartialProcessKey processKey, String message, Throwable t) {
        Map<String, Object> args = new HashMap<>();

        if (processKey != null && queueDao.exists(processKey)) {
            UUID instanceId = processKey.getInstanceId();
            if (instanceId != null) {
                args.put("instanceId", instanceId);
            }
        }

        args.put("message", message);

        if (t != null) {
            t = unwrap(t);
            args.put("stacktrace", stacktraceToString(t));
        }

        return responseTemplates.processError(Response.status(Status.INTERNAL_SERVER_ERROR), args)
                .build();
    }

    private static Throwable unwrap(Throwable t) {
        if (t instanceof ProcessException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    private static String stacktraceToString(Throwable t) {
        StringWriter w = new StringWriter();
        t.printStackTrace(new PrintWriter(w));
        return w.toString();
    }
}
