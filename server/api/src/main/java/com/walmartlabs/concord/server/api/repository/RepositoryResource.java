package com.walmartlabs.concord.server.api.repository;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/api/v1/repository")
public interface RepositoryResource {

    /**
     * Creates a new repository.
     *
     * @param request repository's data
     * @return
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateRepositoryResponse create(@Valid CreateRepositoryRequest request);
}
