package com.walmartlabs.concord.server.ansible;

import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.plugins.ansible.AnsibleConstants;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.process.pipelines.processors.Chain;
import com.walmartlabs.concord.server.process.pipelines.processors.PayloadProcessor;
import com.walmartlabs.concord.server.process.pipelines.processors.RepositoryProcessor;
import com.walmartlabs.concord.server.process.pipelines.processors.RepositoryProcessor.RepositoryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Named
public class PrivateKeyProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(PrivateKeyProcessor.class);

    private final LogManager logManager;
    private final SecretManager secretManager;
    private final ProjectDao projectDao;

    @Inject
    public PrivateKeyProcessor(LogManager logManager, SecretManager secretManager, ProjectDao projectDao) {
        this.logManager = logManager;
        this.secretManager = secretManager;
        this.projectDao = projectDao;
    }

    @Override
    @WithTimer
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();
        Map<String, Object> cfg = payload.getHeader(Payload.REQUEST_DATA_MAP);

        Map<String, Object> ansibleCfg = (Map<String, Object>) cfg.get(AnsibleConfigurationConstants.GROUP_KEY);
        if (ansibleCfg == null) {
            return chain.process(payload);
        }

        Collection<Map<String, Object>> keys = (Collection<Map<String, Object>>) ansibleCfg.get(AnsibleConfigurationConstants.PRIVATE_KEYS);
        if (keys == null) {
            return chain.process(payload);
        }

        String secret = findMatchingSecret(payload, keys);
        if (secret == null) {
            logManager.error(instanceId, "No matching secrets found");
            throw new ProcessException(instanceId, "No matching secrets found");
        }

        UUID orgId = getOrgId(payload);
        KeyPair keyPair = secretManager.getKeyPair(orgId, secret, null);
        if (keyPair == null) {
            logManager.error(instanceId, "Secret not found: " + secret);
            throw new ProcessException(instanceId, "Secret not found: " + secret);
        }

        if (keyPair.getPrivateKey() == null) {
            logManager.error(instanceId, "Private key not found: " + secret);
            throw new ProcessException(instanceId, "Private key not found: " + secret);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path dst = workspace.resolve(AnsibleConstants.PRIVATE_KEY_FILE_NAME);
        if (Files.exists(dst)) {
            logManager.error(instanceId, "File already exists: " + dst);
            throw new ProcessException(instanceId, "File already exists: " + dst);
        }

        try {
            Files.write(dst, keyPair.getPrivateKey());
        } catch (IOException e) {
            logManager.error(instanceId, "Error while copying a private key: " + dst, e);
            throw new ProcessException(instanceId, "Error while copying a private key: " + dst, e);
        }

        log.info("process ['{}'] -> done", instanceId);
        return chain.process(payload);
    }

    private UUID getOrgId(Payload p) {
        UUID projectId = p.getHeader(Payload.PROJECT_ID);

        UUID orgId = null;
        if (projectId != null) {
            orgId = projectDao.getOrgId(projectId);
        }

        if (orgId == null) {
            orgId = OrganizationManager.DEFAULT_ORG_ID;
        }

        return orgId;
    }

    private static String findMatchingSecret(Payload payload, Collection<Map<String, Object>> items) {
        RepositoryInfo info = payload.getHeader(RepositoryProcessor.REPOSITORY_INFO_KEY);
        String repoName = info != null ? info.getName() : "";

        for (Map<String, Object> i : items) {
            String secret = (String) i.get(AnsibleConfigurationConstants.SECRET_KEY);
            if (secret == null || secret.trim().isEmpty()) {
                continue;
            }

            String repo = (String) i.get(AnsibleConfigurationConstants.REPOSITORY_KEY);
            if (repo != null && repoName.matches(repo)) {
                return secret;
            }
        }
        return null;
    }
}
