package com.walmartlabs.concord.server.api.process;

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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Api(value = "Process", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/process")
public interface ProcessResource {

    /**
     * Starts a new process instance.
     *
     * @param in
     * @param parentInstanceId
     * @param sync
     * @return
     */
    @POST
    @ApiOperation("Start a new process instance using the supplied payload archive")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    StartProcessResponse start(@ApiParam InputStream in,
                               @ApiParam @QueryParam("parentId") UUID parentInstanceId,
                               @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                               @ApiParam @QueryParam("out") String[] out);

    /**
     * Starts a new process instance using the specified entry point and provided configuration.
     *
     * @param entryPoint
     * @param parentInstanceId
     * @param sync
     * @return
     */
    @POST
    @ApiOperation("Start a new process using the specified entry point")
    @Path("/{entryPoint}")
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    StartProcessResponse start(@ApiParam @PathParam("entryPoint") String entryPoint,
                               @ApiParam @QueryParam("parentId") UUID parentInstanceId,
                               @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                               @ApiParam @QueryParam("out") String[] out);

    /**
     * Starts a new process instance using the specified entry point and provided configuration.
     *
     * @param entryPoint
     * @param req
     * @param parentInstanceId
     * @param sync
     * @return
     */
    @POST
    @ApiOperation("Start a new process using the specified entry point and provided configuration")
    @Path("/{entryPoint}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    StartProcessResponse start(@ApiParam @PathParam("entryPoint") String entryPoint,
                               @ApiParam Map<String, Object> req,
                               @ApiParam @QueryParam("parentId") UUID parentInstanceId,
                               @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                               @ApiParam @QueryParam("out") String[] out);

    /**
     * Starts a new process instance.
     *
     * @param input
     * @param parentInstanceId
     * @param sync
     * @return
     */
    @POST
    @ApiOperation("Start a new process using multipart request data")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    StartProcessResponse start(@ApiParam MultipartInput input,
                               @ApiParam @Deprecated @QueryParam("parentId") UUID parentInstanceId,
                               @ApiParam @Deprecated @DefaultValue("false") @QueryParam("sync") boolean sync,
                               @ApiParam @Deprecated @QueryParam("out") String[] out);

    /**
     * Starts a new process instance using the specified entry point and multipart request data.
     *
     * @param entryPoint
     * @param input
     * @param parentInstanceId
     * @param sync
     * @return
     */
    @POST
    @ApiOperation("Start a new process using the specified entry point and multipart request data")
    @Path("/{entryPoint}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    StartProcessResponse start(@ApiParam @PathParam("entryPoint") String entryPoint,
                               @ApiParam MultipartInput input,
                               @ApiParam @QueryParam("parentId") UUID parentInstanceId,
                               @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                               @ApiParam @QueryParam("out") String[] out);

    /**
     * Starts a new process instance using the specified entry point and payload archive.
     *
     * @param entryPoint
     * @param in
     * @param parentInstanceId
     * @param sync
     * @return
     */
    @POST
    @ApiOperation("Start a new process using the specified entry point and payload archive")
    @Path("/{entryPoint}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    StartProcessResponse start(@ApiParam @PathParam("entryPoint") String entryPoint,
                               @ApiParam InputStream in,
                               @ApiParam @QueryParam("parentId") UUID parentInstanceId,
                               @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                               @ApiParam @QueryParam("out") String[] out);

    /**
     * Resumes an existing process.
     *
     * @param instanceId
     * @param eventName
     * @param req
     * @return
     */
    @POST
    @ApiOperation("Resume a process")
    @Path("/{id}/resume/{eventName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ResumeProcessResponse resume(@ApiParam @PathParam("id") UUID instanceId,
                                 @ApiParam @PathParam("eventName") @NotNull String eventName,
                                 @ApiParam Map<String, Object> req);

    /**
     * Starts a new child process by forking the start of the specified parent process.
     *
     * @param parentInstanceId
     * @param req
     * @param sync
     * @return
     */
    @POST
    @ApiOperation("Fork a process")
    @Path("/{id}/fork")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    StartProcessResponse fork(@ApiParam @PathParam("id") UUID parentInstanceId,
                              @ApiParam Map<String, Object> req,
                              @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                              @ApiParam @QueryParam("out") String[] out);

    /**
     * Waits for completion of a process.
     *
     * @param instanceId
     * @param timeout
     * @return
     */
    @GET
    @ApiOperation("Wait for a process to finish")
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}/waitForCompletion")
    ProcessEntry waitForCompletion(@ApiParam @PathParam("id") UUID instanceId,
                                   @ApiParam @QueryParam("timeout") @DefaultValue("-1") long timeout);

    /**
     * Forcefully stops a process.
     *
     * @param instanceId
     */
    @DELETE
    @ApiOperation("Forcefully stops a process")
    @Path("/{id}")
    void cancel(@ApiParam @PathParam("id") UUID instanceId);

    /**
     * Forcefully stops a process.
     *
     * @param instanceId
     * @deprecated use {@link #cancel(UUID)}
     */
    @DELETE
    @ApiOperation("Forcefully stops a process")
    @Path("/{id}")
    @Deprecated
    void kill(@ApiParam @PathParam("id") UUID instanceId);

    /**
     * Returns a process instance details.
     *
     * @param instanceId
     * @return
     */
    @GET
    @ApiOperation("Get status of a process")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    ProcessEntry get(@ApiParam @PathParam("id") UUID instanceId);

    /**
     * Returns a process' attachment file.
     *
     * @param instanceId
     * @param attachmentName
     * @return
     */
    @GET
    @ApiOperation("Download a process' attachment")
    @Path("/{id}/attachment/{name:.*}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    Response downloadAttachment(@ApiParam @PathParam("id") UUID instanceId,
                                @PathParam("name") @NotNull @Size(min = 1) String attachmentName);

    /**
     * Lists process attachments.
     *
     * @param instanceId
     * @return
     */
    @GET
    @ApiOperation("List attachments")
    @Path("/{id}/attachment")
    @Produces(MediaType.APPLICATION_JSON)
    List<String> listAttachments(@ApiParam @PathParam("id") UUID instanceId);

    /**
     * List processes for all user's organizations
     *
     * @param projectId
     * @param beforeCreatedAt
     * @param tags
     * @param limit
     * @return
     */
    @GET
    @ApiOperation("List processes for all user's organizations")
    @Produces(MediaType.APPLICATION_JSON)
    List<ProcessEntry> list(@ApiParam @QueryParam("projectId") UUID projectId,
                            @ApiParam @QueryParam("beforeCreatedAt") IsoDateParam beforeCreatedAt,
                            @ApiParam @QueryParam("tags") Set<String> tags,
                            @ApiParam @QueryParam("limit") @DefaultValue("30") int limit);

    /**
     * Returns a list of subprocesses for a given parent process.
     *
     * @param parentInstanceId
     * @param tags
     * @return
     */
    @GET
    @ApiOperation("List subprocesses of a parent process")
    @Path("/{id}/subprocess")
    @Produces(MediaType.APPLICATION_JSON)
    List<ProcessEntry> list(@ApiParam @PathParam("id") UUID parentInstanceId,
                            @ApiParam @QueryParam("tags") Set<String> tags);

    /**
     * Retrieves a process log.
     *
     * @param instanceId
     * @param range
     * @return
     */
    @GET
    @ApiOperation("Retrieve the log")
    @Path("/{id}/log")
    @Produces(MediaType.TEXT_PLAIN)
    Response getLog(@ApiParam @PathParam("id") UUID instanceId,
                    @HeaderParam("range") String range);

    /**
     * Downloads a process state snapshot.
     */
    @GET
    @ApiOperation("Download a process state snapshot")
    @Path("/{id}/state/snapshot")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    Response downloadState(@ApiParam @PathParam("id") UUID instanceId);
}
