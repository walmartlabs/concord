package com.walmartlabs.concord.server.api.user;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api("Role")
@Path("/v1/api/role")
public interface RoleResource {

    @GET
    @ApiOperation("List roles")
    @Produces(MediaType.APPLICATION_JSON)
    List<RoleEntry> get();
}
