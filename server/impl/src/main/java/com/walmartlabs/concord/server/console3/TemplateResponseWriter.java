package com.walmartlabs.concord.server.console3;

import com.walmartlabs.concord.server.sdk.rest.Component;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

import static com.walmartlabs.concord.server.console3.ConsoleModule.BASE_PATH;

@Provider
public class TemplateResponseWriter implements MessageBodyWriter<TemplateResponse>, Component {

    @Context
    private HttpServletRequest request;

    @Context
    private HttpServletResponse response;

    @Inject
    private TemplateRenderer templateRenderer;


    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return TemplateResponse.class.isAssignableFrom(type) && mediaType.isCompatible(MediaType.TEXT_HTML_TYPE);
    }

    @Override
    public void writeTo(TemplateResponse templateResponse, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws WebApplicationException {
        var templateSelectors = request.getHeader("HX-Request") != null ? Set.of("content") : Set.<String>of(); // in case of HTMX requests, render only the "content" part of the template
        var templateSpec = new TemplateSpec(templateResponse.template(), templateSelectors, TemplateMode.HTML, null);
        var context = prepareContext(request, response);
        templateRenderer.render(templateSpec, context, entityStream);
    }

    private static WebContext prepareContext(HttpServletRequest request,
                                             HttpServletResponse response) {

        var app = ThymeleafApp.getInstance(request);
        var ctx = new WebContext(app.buildExchange(request, response));

        ctx.setVariable("request", request);
        ctx.setVariable("basePath", BASE_PATH);

        var principal = UserPrincipal.getCurrent();
        ctx.setVariable("user", principal != null ? principal.getUser() : null);

        return ctx;
    }
}
