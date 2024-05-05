package com.walmartlabs.concord.server.template;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jooq.Configuration;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/v1/template/alias")
@Tag(name = "TemplateAlias")
public class TemplateAliasResource extends AbstractDao implements Resource {

    private final TemplateAliasDao aliasDao;

    @Inject
    public TemplateAliasResource(@MainDB Configuration cfg, TemplateAliasDao aliasDao) {
        super(cfg);
        this.aliasDao = aliasDao;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Create or update a template alias", operationId = "createOrUpdateTemplate")
    public TemplateAliasResponse createOrUpdate(@Valid TemplateAliasEntry request) {
        assertAdmin();

        tx(tx -> {
            aliasDao.delete(request.getAlias());
            aliasDao.insert(request.getAlias(), request.getUrl());
        });

        return new TemplateAliasResponse();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List current template aliases", operationId = "listTemplates")
    public List<TemplateAliasEntry> list() {
        return aliasDao.list();
    }

    @DELETE
    @Path("/{alias}")
    @Operation(description = "Delete existing template alias", operationId = "deleteTemplate")
    public TemplateAliasResponse delete(@PathParam("alias") @ConcordKey String alias) {

        assertAdmin();
        aliasDao.delete(alias);
        return new TemplateAliasResponse();
    }

    private static void assertAdmin() {
        if (!Roles.isAdmin()) {
            throw new UnauthorizedException("Only admins can do that");
        }
    }
}
