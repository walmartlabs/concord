package com.walmartlabs.concord.agent.api;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;

@Path("/api/v1/job")
public interface JobResource {

    /**
     * Starts a new job asynchronously.
     *
     * @param in         job's payload
     * @param type       type of the job
     * @param entryPoint
     * @return ID of the job
     * @throws Exception
     */
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_PLAIN)
    String start(InputStream in,
                 @QueryParam("type") @NotNull JobType type,
                 @QueryParam("entryPoint") @NotNull String entryPoint) throws Exception;

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
     * @param id ID of a job
     * @param waitToFinish
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
}
