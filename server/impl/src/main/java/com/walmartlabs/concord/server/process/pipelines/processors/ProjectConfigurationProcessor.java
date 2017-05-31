package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.model.ProjectDefinitionUtils;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.project.ProjectConfigurationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Merges the project's configuration with the request's data.
 * The order is {@code project file <- project's DB data <- user's request}.
 */
@Named
public class ProjectConfigurationProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProjectConfigurationProcessor.class);

    private final ProjectConfigurationDao cfgDao;

    @Inject
    public ProjectConfigurationProcessor(ProjectConfigurationDao cfgDao) {
        this.cfgDao = cfgDao;
    }

    @Override
    @WithTimer
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        // project file's configuration
        Map<String, Object> fileCfg = new HashMap<>();

        ProjectDefinition pd = payload.getHeader(Payload.PROJECT_DEFINITION);
        if (pd != null) {
            String[] activeProfiles = payload.getHeader(Payload.ACTIVE_PROFILES);
            if (activeProfiles == null) {
                log.warn("process ['{}'] -> no active profiles found", payload.getInstanceId());
            } else {
                fileCfg = ProjectDefinitionUtils.getVariables(pd, Arrays.asList(activeProfiles));
            }
        }

        // configuration from the db
        Map<String, Object> dbCfg = Collections.emptyMap();
        String projectName = payload.getHeader(Payload.PROJECT_NAME);
        if (projectName != null) {
            dbCfg = cfgDao.get(projectName);
            if (dbCfg == null) {
                dbCfg = Collections.emptyMap();
            }
        }

        // user's request
        Map<String, Object> request = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (request == null) {
            request = Collections.emptyMap();
        }

        Map<String, Object> result = ConfigurationUtils.deepMerge(fileCfg, dbCfg, request);
        payload = payload.putHeader(Payload.REQUEST_DATA_MAP, result);
        return chain.process(payload);
    }
}
