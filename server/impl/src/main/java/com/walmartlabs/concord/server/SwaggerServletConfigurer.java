package com.walmartlabs.concord.server;

import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.net.URL;

@Named
public class SwaggerServletConfigurer implements ServletConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SwaggerServletConfigurer.class);

    @Override
    public void configure(ServletContextHandler servletHandler) {
        URL url = ClassLoader.getSystemResource("com/walmartlabs/concord/server/api/swagger/");
        if (url == null) {
            log.warn("No Swagger definitions found.");
            return;
        }

        String baseDir = url.toExternalForm();

        ServletHolder h = new ServletHolder("swagger", DefaultServlet.class);
        h.setInitParameter("resourceBase", baseDir);
        h.setInitParameter("pathInfoOnly", "true");
        servletHandler.addServlet(h, "/swagger/*");

        log.info("Serving Swagger definitions from {}", baseDir);
    }
}
