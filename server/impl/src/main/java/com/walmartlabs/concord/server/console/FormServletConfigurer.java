package com.walmartlabs.concord.server.console;

import com.walmartlabs.concord.server.ServletConfigurer;
import com.walmartlabs.concord.server.cfg.FormServerConfiguration;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class FormServletConfigurer implements ServletConfigurer {

    private static final Logger log = LoggerFactory.getLogger(FormServletConfigurer.class);

    private final FormServerConfiguration cfg;

    @Inject
    public FormServletConfigurer(FormServerConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public void configure(ServletContextHandler servletHandler) {
        String baseDir = cfg.getBaseDir().toAbsolutePath().toString();

        ServletHolder h = new ServletHolder("forms", DefaultServlet.class);
        h.setInitParameter("acceptRanges", "true");
        h.setInitParameter("dirAllowed", "false");
        h.setInitParameter("resourceBase", baseDir);
        h.setInitParameter("pathInfoOnly", "true");
        h.setInitParameter("redirectWelcome", "false");
        servletHandler.addServlet(h, CustomFormService.FORMS_PATH_PREFIX + "*");

        log.info("Serving custom forms from {}", baseDir);
    }
}
