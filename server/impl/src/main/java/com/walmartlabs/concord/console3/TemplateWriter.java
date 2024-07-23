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

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.console3.resources.ConsoleResource;
import com.walmartlabs.concord.server.sdk.rest.Component;
import com.walmartlabs.concord.server.security.UserPrincipal;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.ResourceCodeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.TEXT_HTML;

// TODO streaming output
@Provider
@Produces(TEXT_HTML)
public class TemplateWriter implements MessageBodyWriter<TemplateResponse>, Component {

    private static final Logger log = LoggerFactory.getLogger(TemplateWriter.class);

    private final TemplateEngine templateEngine;

    public TemplateWriter() {
        var resolver = new ResourceCodeResolver("com/walmartlabs/concord/console3", ConsoleResource.class.getClassLoader());
        this.templateEngine = TemplateEngine.create(resolver, java.nio.file.Path.of("target/jte-classes"), ContentType.Html);
        log.info("Template engine initialized");
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return TemplateResponse.class.isAssignableFrom(type);
    }

    @Override
    public void writeTo(TemplateResponse s, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
        var params = ImmutableMap.<String, Object>builder();

        var userContext = Optional.ofNullable(UserPrincipal.getCurrent())
                .map(principal -> new UserContext(principal.getUsername(), principal.getUsername(), principal.getUsername()));
        userContext.ifPresent(ctx -> params.put("userContext", ctx));

        params.putAll(s.params());

        var output = new StringOutput();
        templateEngine.render(s.template(), params.build(), output);

        entityStream.write(output.toString().getBytes());
    }
}
