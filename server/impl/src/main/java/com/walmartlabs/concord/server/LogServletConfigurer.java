package com.walmartlabs.concord.server;

import com.walmartlabs.concord.server.cfg.LogStoreConfiguration;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class LogServletConfigurer implements ServletConfigurer {

    private static final Logger log = LoggerFactory.getLogger(LogServletConfigurer.class);

    private final LogStoreConfiguration cfg;

    @Inject
    public LogServletConfigurer(LogStoreConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public void configure(ServletContextHandler servletHandler) {
        String baseDir = cfg.getBaseDir().toAbsolutePath().toString();

        ServletHolder h = new ServletHolder("logs", DefaultServlet.class);
        h.setInitParameter("acceptRanges", "true");
        h.setInitParameter("dirAllowed", "false");
        h.setInitParameter("resourceBase", baseDir);
        h.setInitParameter("pathInfoOnly", "true");
        h.setInitParameter("cacheControl", "max-age=0");
        servletHandler.addServlet(h, "/logs/*");

        log.info("Serving log files from {}", baseDir);
    }
}
