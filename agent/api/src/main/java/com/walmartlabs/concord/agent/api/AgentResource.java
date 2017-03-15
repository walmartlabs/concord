package com.walmartlabs.concord.agent.api;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;

@Path("/api/v1/job")
public interface AgentResource {

    /**
     * Starts new job asynchronously.
     *
     * @param instanceId
     * @param type       type of the job
     * @param entryPoint
     * @param in         job's payload
     * @return ID of the job
     * @throws Exception
     */
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    void start(@QueryParam("instanceId") @NotNull String instanceId,
               @QueryParam("type") @NotNull JobType type,
               @QueryParam("entryPoint") @NotNull String entryPoint,
               InputStream in) throws Exception;

    /**
     * @param id ID of a job
     * @return status of a job
     */
    @GET
    @Path("/{id}/status")
    @Produces(MediaType.TEXT_PLAIN)
    JobStatus getStatus(@PathParam("id") @NotNull String id);

    /**
     * Interrupts all running jobs.
     */
    @DELETE
    void cancelAll();

    /**
     * Interrupts a job.
     *
     * @param id           ID of a job
     * @param waitToFinish blocks until job is finished
     */
    @DELETE
    @Path("/{id}")
    void cancel(@PathParam("id") String id,
                @QueryParam("waitToFinish") boolean waitToFinish);

    /**
     * @return the number of currently running jobs.
     */
    @GET
    @Path("/count")
    @Produces(MediaType.TEXT_PLAIN)
    int count();

    /**
     * Downloads an archive with job's attachments.
     *
     * @param id ID of a job
     * @return
     */
    @GET
    @Path("/{id}/attachment/archive")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    Response downloadAttachments(@PathParam("id") String id);

    /**
     * Stream a job's log file, sending data as the job progresses.
     *
     * @param id ID of a job
     * @return
     */
    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Path("/{id}")
    Response streamLog(@PathParam("id") @NotNull String id);

    @GET
    @Path("/ping")
    @Produces(MediaType.APPLICATION_JSON)
    PingResponse ping();
}
