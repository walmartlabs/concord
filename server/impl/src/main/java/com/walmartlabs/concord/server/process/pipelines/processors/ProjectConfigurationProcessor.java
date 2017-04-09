package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.server.project.ProjectConfigurationDao;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;

@Named
public class ProjectConfigurationProcessor implements PayloadProcessor {

    private final ProjectConfigurationDao cfgDao;

    @Inject
    public ProjectConfigurationProcessor(ProjectConfigurationDao cfgDao) {
        this.cfgDao = cfgDao;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        String projectName = payload.getHeader(Payload.PROJECT_NAME);
        if (projectName == null) {
            return chain.process(payload);
        }

        Map<String, Object> cfg = cfgDao.get(projectName);
        if (cfg == null) {
            return chain.process(payload);
        }

        Map<String, Object> request = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (request == null) {
            return chain.process(payload);
        }

        payload = payload.putHeader(Payload.REQUEST_DATA_MAP,
                ConfigurationUtils.deepMerge(cfg, request));

        return chain.process(payload);
    }
}
