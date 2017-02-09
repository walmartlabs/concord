package com.walmartlabs.concord.server.api.repository;

import com.walmartlabs.concord.common.validation.ConcordId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api("Repository")
@Path("/api/v1/repository")
public interface RepositoryResource {

    /**
     * Creates a new repository.
     *
     * @param request repository's data
     * @return
     */
    @POST
    @ApiOperation("Create a new repository")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateRepositoryResponse create(@ApiParam @Valid CreateRepositoryRequest request);

    /**
     * Returns an existing repository.
     * @param id
     * @return
     */
    @GET
    @ApiOperation("Get an existing repository")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    RepositoryEntry get(@ApiParam @PathParam("id") @ConcordId String id);

    /**
     * List repositories.
     *
     * @param sortBy
     * @param asc
     * @return
     */
    @GET
    @ApiOperation("List repositories")
    @Produces(MediaType.APPLICATION_JSON)
    List<RepositoryEntry> list(@ApiParam("Sorting field") @QueryParam("sortBy") @DefaultValue("name") String sortBy,
                               @ApiParam("Order") @QueryParam("asc") @DefaultValue("true") boolean asc);

    /**
     * Update an existing repository.
     *
     * @param id
     * @param request
     * @return
     */
    @PUT
    @ApiOperation("Update an existing repository")
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateRepositoryResponse update(@ApiParam("Repository ID") @PathParam("id") @ConcordId String id,
                                    @ApiParam("Repository's new parameters") @Valid UpdateRepositoryRequest request);

    /**
     * Delete an existing repository.
     *
     * @param id
     * @return
     */
    @DELETE
    @ApiOperation("Delete an existing repository")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteRepositoryResponse delete(@ApiParam("Repository ID") @PathParam("id") @ConcordId String id);
}
