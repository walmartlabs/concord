package com.walmartlabs.concord.server.boot.resteasy;

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

import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.jboss.resteasy.plugins.server.servlet.ListenerBootstrap;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.util.GetRestful;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.ws.rs.ext.Provider;
import java.util.ArrayList;

import static java.util.Objects.requireNonNull;

/**
 * Based on the original code from {@code pkg:maven/org.jboss.resteasy/resteasy-guice@4.7.9.Final}.
 */
@WebListener
public class ResteasyBootstrapListener implements ServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(ResteasyBootstrapListener.class);

    private final Injector injector;
    private ResteasyDeployment deployment;

    @Inject
    public ResteasyBootstrapListener(Injector injector) {
        this.injector = requireNonNull(injector);
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        var config = new ListenerBootstrap(event.getServletContext());

        deployment = config.createDeployment();

        var servletContext = event.getServletContext();
        servletContext.setAttribute(ResteasyDeployment.class.getName(), deployment);

        deployment.start();

        for (var injector = this.injector; injector != null; injector = injector.getParent()) {
            processInjector(deployment, injector);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (deployment == null) {
            return;
        }
        deployment.stop();
    }

    private static void processInjector(ResteasyDeployment deployment, Injector injector) {
        var rootResourceBindings = new ArrayList<Binding<?>>();

        var providerFactory = deployment.getProviderFactory();
        for (var binding : injector.getBindings().values()) {
            var type = (Object) binding.getKey().getTypeLiteral().getRawType();
            if (type instanceof Class<?> beanClass) {
                if (GetRestful.isRootResource(beanClass)) {
                    rootResourceBindings.add(binding);
                }
                if (beanClass.isAnnotationPresent(Provider.class)) {
                    log.info("registering provider instance for {}", beanClass.getName());
                    providerFactory.registerProviderInstance(binding.getProvider().get());
                }
            }
        }

        var registry = deployment.getRegistry();
        for (var binding : rootResourceBindings) {
            var beanClass = (Class<?>) binding.getKey().getTypeLiteral().getType();
            var resourceFactory = new GuiceResourceFactory(binding.getProvider(), beanClass);
            log.info("registering factory for {}", beanClass.getName());
            registry.addResourceFactory(resourceFactory);
        }
    }
}
