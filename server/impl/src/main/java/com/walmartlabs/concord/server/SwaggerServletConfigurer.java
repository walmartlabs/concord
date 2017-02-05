package com.walmartlabs.concord.server;

import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwaggerServletConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SwaggerServletConfigurer.class);

    public void configure(ServletContextHandler servletHandler) {
        String baseDir = ClassLoader.getSystemResource("com/walmartlabs/concord/server/api/swagger/").toExternalForm();

        ServletHolder h = new ServletHolder("swagger", DefaultServlet.class);
        h.setInitParameter("resourceBase", baseDir);
        h.setInitParameter("pathInfoOnly", "true");
        servletHandler.addServlet(h, "/swagger/*");

        log.info("Serving Swagger definitions from {}", baseDir);
    }
}
