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
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Stream;

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
        var context = prepareContext(request, response, templateResponse.extraVars());
        templateRenderer.render(templateSpec, context, entityStream);
    }

    private static WebContext prepareContext(HttpServletRequest request,
                                             HttpServletResponse response,
                                             Map<String, Object> extraVars) {

        var app = ThymeleafApp.getInstance(request);
        var ctx = new WebContext(app.buildExchange(request, response));

        // locale

        var locale = Optional.ofNullable(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE))
                .flatMap(TemplateResponseWriter::getLocaleFromAcceptLanguageHeader)
                .orElseGet(Locale::getDefault);

        ctx.setLocale(locale);

        // variables

        var principal = UserPrincipal.getCurrent();
        ctx.setVariable("user", principal != null ? principal.getUser() : null);

        ctx.setVariable("request", request);
        ctx.setVariable("basePath", BASE_PATH);
        ctx.setVariables(extraVars);

        return ctx;
    }

    static Optional<Locale> getLocaleFromAcceptLanguageHeader(String v) {
        return Arrays.stream(v.split(","))
                .map(s -> s.trim().split(";"))
                .filter(parts -> parts.length > 0)
                .map(parts -> parts[0])
                .flatMap(key -> {
                    var localeParts = key.split("-");
                    if (localeParts.length == 1) {
                        return Stream.of(new Locale(localeParts[0]));
                    } else if (localeParts.length == 2) {
                        return Stream.of(new Locale(localeParts[0], localeParts[1]));
                    } else {
                        return Stream.empty();
                    }
                })
                .findFirst();
    }
}
