package com.walmartlabs.concord.server.boot.statics;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.server.boot.ContextHandlerConfigurator;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;

/**
 * Configures Jetty's {@link org.eclipse.jetty.server.Handler} to serve Concord's static resources.
 */
public class StaticResourcesConfigurator implements ContextHandlerConfigurator {

    @Override
    public void configure(ContextHandlerCollection collection) {
        collection.addHandler(classpathResourceHandler("/resources/console", "/com/walmartlabs/concord/server/console/static"));
    }

    private static ContextHandler classpathResourceHandler(String context, String path) {
        ContextHandler handler = new ContextHandler();

        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirAllowed(false);

        Resource resource = ResourceFactory.root().newClassLoaderResource(path);
        handler.setBaseResource(resource);
        handler.setHandler(resourceHandler);
        handler.setContextPath(context);

        return handler;
    }
}
