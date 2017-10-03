package com.walmartlabs.concord.server.api.process;

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Api("Process")
@Path("/api/v1/process")
public interface ProcessResource {

    /**
     * Starts a new process instance.
     *
     * @param in
     * @return
     */
    @POST
    @ApiOperation("Starts a new process instance using a supplied payload archive")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    StartProcessResponse start(@ApiParam InputStream in,
                               @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync);

    /**
     * Starts a new process instance using the specified project and provided configuration.
     *
     * @param sync
     * @return
     */
    @POST
    @ApiOperation("Start a new process using a specified project")
    @Path("/{entryPoint}")
    StartProcessResponse start(@ApiParam @PathParam("entryPoint") String entryPoint,
                               @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync);


    /**
     * Starts a new process instance using the specified project and provided configuration.
     *
     * @param req
     * @param sync
     * @return
     */
    @POST
    @ApiOperation("Start a new process using a specified project and provided configuration")
    @Path("/{entryPoint}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    StartProcessResponse start(@ApiParam @PathParam("entryPoint") String entryPoint,
                               @ApiParam Map<String, Object> req,
                               @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync);

    /**
     * Starts a new process instance.
     *
     * @param input
     * @param sync
     * @return
     */
    @POST
    @ApiOperation("Start a new process using multipart request data")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    StartProcessResponse start(@ApiParam MultipartInput input,
                               @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync);

    /**
     * Starts a new process instance using a specified project.
     *
     * @param entryPoint
     * @param input
     * @param sync
     * @return
     */
    @POST
    @ApiOperation("Start a new process using a specified project entry point and multipart request data")
    @Path("/{entryPoint}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    StartProcessResponse start(@ApiParam @PathParam("entryPoint") String entryPoint,
                               @ApiParam MultipartInput input,
                               @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync);

    /**
     * Starts a new process instance using a specified project and a payload archive.
     *
     * @param projectName
     * @param in
     * @param sync
     * @return
     */
    @POST
    @ApiOperation("Start a new process using a project name and a payload archive")
    @Path("/{projectName}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    StartProcessResponse start(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                               @ApiParam InputStream in,
                               @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync);

    @POST
    @ApiOperation("Resume a process")
    @Path("/{id}/resume/{eventName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ResumeProcessResponse resume(@ApiParam @PathParam("id") UUID instanceId,
                                 @ApiParam @PathParam("eventName") @NotNull String eventName,
                                 @ApiParam Map<String, Object> req);

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
    @ApiOperation("Forcefully stop a process")
    @Path("/{id}")
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
    @Path("/{id}/attachment/{name}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    Response downloadAttachment(@ApiParam @PathParam("id") UUID instanceId,
                                @PathParam("name") @NotNull @Size(min = 1) String attachmentName);

    /**
     * Returns entries from the process queue.
     *
     * @return
     */
    @GET
    @ApiOperation("List processes")
    @Produces(MediaType.APPLICATION_JSON)
    List<ProcessEntry> list();

    /**
     * Returns a list of child processes for a given parent process.
     *
     * @param parentInstanceId
     * @return
     */
    @GET
    @ApiOperation("List child processes of a parent process")
    @Path("/{id}/children")
    @Produces(MediaType.APPLICATION_JSON)
    List<ProcessEntry> list(@ApiParam @PathParam("id") UUID parentInstanceId);

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
