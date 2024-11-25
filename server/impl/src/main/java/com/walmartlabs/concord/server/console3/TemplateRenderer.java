package com.walmartlabs.concord.server.console3;

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

import com.walmartlabs.concord.server.user.UserEntry;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JavaxServletWebApplication;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class TemplateRenderer {

    private final ITemplateEngine engine;

    public TemplateRenderer() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(true);
        resolver.setCacheTTLMs(1L);

        var engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);

        this.engine = engine;
    }

    public void render(String resource,
                       HttpServletRequest request,
                       HttpServletResponse response,
                       Optional<UserEntry> user,
                       OutputStream out) {

        var servletContext = request.getServletContext();
        var app = JavaxServletWebApplication.buildApplication(servletContext);

        var templateContext = new WebContext(app.buildExchange(request, response));
        templateContext.setVariable("user", user.orElse(null));

        var writer = new OutputStreamWriter(out);
        engine.process(resource, templateContext, writer);
    }
}
