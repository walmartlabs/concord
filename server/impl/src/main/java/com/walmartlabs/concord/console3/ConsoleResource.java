package com.walmartlabs.concord.console3;

import com.walmartlabs.concord.server.sdk.rest.Resource;
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
public class ConsoleResource implements Resource {

    private final TemplateEngine templateEngine;

    public ConsoleResource() {
        var resolver = new ResourceCodeResolver("com/walmartlabs/concord/console3", ConsoleResource.class.getClassLoader());
        this.templateEngine = TemplateEngine.create(resolver, java.nio.file.Path.of("target/jte-classes"), ContentType.Html);
    }

    @POST
    @Produces(MediaType.TEXT_HTML)
    @Path("/clicked")
    public Response clicked() {
        return renderAuthenticated(Status.OK, "clicked.jte");
    }

    @GET
    public Response index() {
        return get("/index.html");
    }

    @GET
    @Path("{path:.*}")
    public Response get(@PathParam("path") String path) {
        if (path == null || path.isEmpty() || path.equals("/") || path.equals("/index.html")) {
            return renderAuthenticated(Status.OK, "index.jte");
        } else if (path.equals("htmx.min.js")) {
            return renderAnon(Status.OK, "htmx.min.js");
        }

        return renderAnon(Status.NOT_FOUND, "404.jte");
    }

    private Response renderAuthenticated(Status status, String template) {
        return UserContext.getCurrent().map(userContext -> {
            var pageContext = new PageContext("/console3");
            var params = Map.<String, Object>of(
                    "pageContext", pageContext,
                    "userContext", userContext
            );
            return html(status, template, params);
        }).orElseGet(() -> renderAnon(Status.UNAUTHORIZED, "401.jte"));
    }

    private Response renderAnon(Status status, String template) {
        var pageContext = new PageContext("/console3");
        var params = Map.<String, Object>of("pageContext", pageContext);
        return html(status, template, params);
    }

    private Response html(Status status, String template, Map<String, Object> params) {
        var output = new StringOutput();
        templateEngine.render(template, params, output);
        return Response.status(status)
                .entity(output.toString())
                .type(MediaType.TEXT_HTML)
                .build();
    }
}
