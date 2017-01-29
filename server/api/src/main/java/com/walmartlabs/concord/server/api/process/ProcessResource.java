package com.walmartlabs.concord.server.api.process;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.Map;

@Path("/api/v1/process")
public interface ProcessResource {

    /**
     * Starts a new process instance asynchronously.
     *
     * @param in
     * @return an ID of the created instance.
     */
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    StartProcessResponse start(InputStream in);

    /**
     * Starts a new process instance asynchronously from a specified project.
     *
     * @param req
     * @return an ID of the created instance;
     */
    @POST
    @Path("/{entryPoint}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    StartProcessResponse start(@PathParam("entryPoint") String entryPoint, Map<String, Object> req);

    /**
     * Waits for completion of a process.
     *
     * @param instanceId
     * @param timeout
     * @return
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}/waitForCompletion")
    ProcessStatusResponse waitForCompletion(@PathParam("id") String instanceId, @QueryParam("timeout") @DefaultValue("-1") long timeout);

    /**
     * Forcefully stops a process.
     *
     * @param instanceId
     */
    @DELETE
    @Path("/{id}")
    void kill(@PathParam("id") String instanceId);

    /**
     * Returns a process instance details.
     *
     * @param instanceId
     * @return
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    ProcessStatusResponse get(@PathParam("id") String instanceId);
}
