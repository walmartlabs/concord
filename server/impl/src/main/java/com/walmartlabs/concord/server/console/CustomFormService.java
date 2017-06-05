package com.walmartlabs.concord.server.console;

import com.walmartlabs.concord.common.validation.ConcordId;

import javax.ws.rs.*;
import javax.ws.rs.core.*;

@Path("/api/service/custom_form")
public interface CustomFormService {

    String FORMS_PATH_PREFIX = "/forms/";
    String FORMS_PATH_PATTERN = FORMS_PATH_PREFIX + "%s/%s/";
    String DATA_FILE_TEMPLATE = "data = %s;";

    @POST
    @Path("{processInstanceId}/{formInstanceId}/start")
    @Produces(MediaType.APPLICATION_JSON)
    FormSessionResponse startSession(@PathParam("processInstanceId") @ConcordId String processInstanceId,
                                     @PathParam("formInstanceId") @ConcordId String formInstanceId);

    @POST
    @Path("{processInstanceId}/{formInstanceId}/continue")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    Response continueSession(@Context UriInfo uriInfo,
                             @Context HttpHeaders headers,
                             @PathParam("processInstanceId") @ConcordId String processInstanceId,
                             @PathParam("formInstanceId") @ConcordId String formInstanceId,
                             MultivaluedMap<String, String> data);
}
