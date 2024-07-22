package com.walmartlabs.concord.console3;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.UserPrincipal;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.ResourceCodeResolver;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Map;

@Path("/console3")
@Produces(MediaType.TEXT_HTML)
public class ConsoleResource implements Resource {

    private final TemplateEngine templateEngine;

    public ConsoleResource() {
        var resolver = new ResourceCodeResolver("com/walmartlabs/concord/console3", ConsoleResource.class.getClassLoader());
        this.templateEngine = TemplateEngine.create(resolver, java.nio.file.Path.of("target/jte-classes"), ContentType.Html);
    }

    @GET
    public Response index() {
        return get("/index.html");
    }

    @GET
    @Path("{path:.*}")
    public Response get(@PathParam("path") String path) {
        // TODO consider handling auth in a FilterChainConfigurator
        var principal = UserPrincipal.getCurrent();
        if (principal == null) {
            return renderAnon(Status.UNAUTHORIZED, "index.jte");
        }

        var userContext = new UserContext(principal.getUsername(), principal.getUsername(), principal.getUsername());

        if (path == null || path.isEmpty() || path.equals("/") || path.equals("/index.html")) {
            path = "/";
        }

        switch (path) {
            case "/":
                return render(Status.OK, userContext, "index.jte");
            case "htmx.min.js":
                return serve(Status.OK, "text/javascript", "/com/walmartlabs/concord/console3/htmx.min.js");
            case "modern-normalize.min.css":
                return serve(Status.OK, "text/css", "/com/walmartlabs/concord/console3/modern-normalize.min.css");
            default:
                return render(Status.NOT_FOUND, userContext, "404.jte");
        }
    }

    @POST
    @Path("/clicked")
    public Response clicked() {
        // TODO consider handling auth in a FilterChainConfigurator
        var principal = UserPrincipal.getCurrent();
        if (principal == null) {
            return renderAnon(Status.UNAUTHORIZED, "index.jte");
        }

        var userContext = new UserContext(principal.getUsername(), principal.getUsername(), principal.getUsername());
        return render(Status.OK, userContext, "clicked.jte");
    }

    private Response renderAnon(Status status, String template) {
        var pageContext = new PageContext("/console3");
        var params = Map.<String, Object>of("pageContext", pageContext);
        return html(template, params)
                .status(status)
                .build();
    }

    private Response render(Status status, UserContext userContext, String template) {
        var pageContext = new PageContext("/console3");
        var params = Map.<String, Object>of(
                "pageContext", pageContext,
                "userContext", userContext
        );
        return html(template, params)
                .status(status)
                .build();
    }

    private Response serve(Status status, String mediaType, String resourcePath) {
        var resource = ConsoleResource.class.getResourceAsStream(resourcePath);
        if (resource == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        return Response.ok(resource, mediaType)
                .status(status)
                .build();
    }

    private Response.ResponseBuilder html(String template, Map<String, Object> params) {
        var output = new StringOutput();
        templateEngine.render(template, params, output);
        return Response.ok(output.toString(), MediaType.TEXT_HTML);
    }
}
