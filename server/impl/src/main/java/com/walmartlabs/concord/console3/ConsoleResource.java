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

    private static final PageContext DEFAULT_PAGE_CONTEXT = new PageContext("/console3");

    private final TemplateEngine templateEngine;

    public ConsoleResource() {
        var resolver = new ResourceCodeResolver("com/walmartlabs/concord/console3", ConsoleResource.class.getClassLoader());
        this.templateEngine = TemplateEngine.create(resolver, java.nio.file.Path.of("target/jte-classes"), ContentType.Html);
    }

    @POST
    @Produces(MediaType.TEXT_HTML)
    @Path("/clicked")
    public Response clicked() {
        return render("clicked.jte");
    }

    @GET
    public Response index() {
        return get("/index.html");
    }

    @GET
    @Path("{path:.*}")
    public Response get(@PathParam("path") String path) {
        if (path == null || path.isEmpty() || path.equals("/") || path.equals("/index.html")) {
            return render("index.jte");
        } else if (path.equals("htmx.min.js")) {
            return render("htmx.min.js");
        }

        return render(Status.NOT_FOUND, "404.jte");
    }

    private Response render(String template) {
        return render(Status.OK, template);
    }

    private Response render(Status status, String template) {
        var output = new StringOutput();
        templateEngine.render(template, Map.of("pageContext", DEFAULT_PAGE_CONTEXT), output);
        return Response.status(status)
                .entity(output.toString())
                .type(MediaType.TEXT_HTML)
                .build();
    }
}
