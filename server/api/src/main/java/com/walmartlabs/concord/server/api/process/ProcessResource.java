package com.walmartlabs.concord.server.api.process;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.Map;

@Api("Process")
@Path("/api/v1/process")
public interface ProcessResource {

    /**
     * Starts a new process instance asynchronously.
     *
     * @param in
     * @return
     */
    @POST
    @ApiOperation("Starts a new process instance using a supplied payload archive")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    StartProcessResponse start(@ApiParam InputStream in);

    /**
     * Starts a new process instance asynchronously using a specified project.
     *
     * @param req
     * @return
     */
    @POST
    @ApiOperation("Start a new process using a specified project entry point")
    @Path("/{entryPoint}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    StartProcessResponse start(@ApiParam @PathParam("entryPoint") String entryPoint,
                               @ApiParam Map<String, Object> req);

    /**
     * Starts a new process instance asynchronously using a specified project.
     *
     * @param entryPoint
     * @param input
     * @return
     */
    @POST
    @ApiOperation("Start a new process using a specified project entry point and multipart request data")
    @Path("/{entryPoint}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    StartProcessResponse start(@ApiParam @PathParam("entryPoint") String entryPoint,
                               @ApiParam MultipartInput input);

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
    ProcessStatusResponse waitForCompletion(@ApiParam @PathParam("id") String instanceId,
                                            @ApiParam @QueryParam("timeout") @DefaultValue("-1") long timeout);

    /**
     * Forcefully stops a process.
     *
     * @param instanceId
     */
    @DELETE
    @ApiOperation("Forcefully stop a process")
    @Path("/{id}")
    void kill(@ApiParam @PathParam("id") String instanceId);

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
    ProcessStatusResponse get(@ApiParam @PathParam("id") String instanceId);
}
