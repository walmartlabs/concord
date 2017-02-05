package com.walmartlabs.concord.server;

import com.walmartlabs.concord.server.cfg.LogStoreConfiguration;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogServletConfigurer {

    private static final Logger log = LoggerFactory.getLogger(LogServletConfigurer.class);

    private final LogStoreConfiguration logStoreCfg;

    public LogServletConfigurer(LogStoreConfiguration logStoreCfg) {
        this.logStoreCfg = logStoreCfg;
    }

    public void configure(ServletContextHandler servletHandler) {
        String baseDir = logStoreCfg.getBaseDir().toAbsolutePath().toString();

        ServletHolder h = new ServletHolder("logs", DefaultServlet.class);
        h.setInitParameter("acceptRanges", "true");
        h.setInitParameter("resourceBase", baseDir);
        h.setInitParameter("pathInfoOnly", "true");
        h.setInitParameter("cacheControl", "max-age=0");
        servletHandler.addServlet(h, "/logs/*");

        log.info("Serving log files from {}", baseDir);
    }
}
