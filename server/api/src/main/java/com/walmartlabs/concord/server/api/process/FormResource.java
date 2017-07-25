package com.walmartlabs.concord.server.api.process;

import com.walmartlabs.concord.common.validation.ConcordId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Api("Form")
@Path("/api/v1/process")
public interface FormResource {

    @GET
    @ApiOperation("List the available forms")
    @Path("/{processInstanceId}/form")
    @Produces(MediaType.APPLICATION_JSON)
    List<FormListEntry> list(@ApiParam @PathParam("processInstanceId") UUID processInstanceId);

    /**
     * Returns the current state of a form instance.
     *
     * @param formInstanceId
     * @return
     */
    @GET
    @ApiOperation("Get the current state of a form")
    @Path("/{processInstanceId}/form/{formInstanceId}")
    @Produces(MediaType.APPLICATION_JSON)
    FormInstanceEntry get(@ApiParam @PathParam("processInstanceId") UUID processInstanceId,
                          @ApiParam @PathParam("formInstanceId") @ConcordId String formInstanceId);

    /**
     * Submits form instance's data, potentially resuming a suspended process.
     *
     * @param formInstanceId
     * @param data
     * @return
     */
    @POST
    @ApiOperation("Submit form data")
    @Path("/{processInstanceId}/form/{formInstanceId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    FormSubmitResponse submit(@ApiParam @PathParam("processInstanceId") UUID processInstanceId,
                              @ApiParam @PathParam("formInstanceId") @ConcordId String formInstanceId,
                              @ApiParam Map<String, Object> data);
}
