package com.walmartlabs.concord.server.console;

import com.walmartlabs.concord.common.validation.ConcordId;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.UUID;

@Path("/api/service/custom_form")
public interface CustomFormService {

    @POST
    @Path("{processInstanceId}/{formInstanceId}/start")
    @Produces(MediaType.APPLICATION_JSON)
    FormSessionResponse startSession(@PathParam("processInstanceId") UUID processInstanceId,
                                     @PathParam("formInstanceId") @ConcordId String formInstanceId);

    @POST
    @Path("{processInstanceId}/{formInstanceId}/continue")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    Response continueSession(@Context UriInfo uriInfo,
                             @Context HttpHeaders headers,
                             @PathParam("processInstanceId") UUID processInstanceId,
                             @PathParam("formInstanceId") @ConcordId String formInstanceId,
                             MultivaluedMap<String, String> data);
}
