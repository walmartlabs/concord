package com.walmartlabs.concord.server.ansible;

import com.walmartlabs.concord.plugins.ansible.AnsibleConstants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.pipelines.processors.PayloadProcessor;
import com.walmartlabs.concord.server.project.ProjectConfigurationDao;
import com.walmartlabs.concord.server.project.RepositoryProcessor;
import com.walmartlabs.concord.server.project.RepositoryProcessor.RepositoryInfo;
import com.walmartlabs.concord.server.security.secret.KeyPair;
import com.walmartlabs.concord.server.security.secret.SecretManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

@Named
public class PrivateKeyProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(PrivateKeyProcessor.class);

    private final ProjectConfigurationDao cfgDao;
    private final SecretManager secretManager;

    @Inject
    public PrivateKeyProcessor(ProjectConfigurationDao cfgDao, SecretManager secretManager) {
        this.cfgDao = cfgDao;
        this.secretManager = secretManager;
    }

    @Override
    public Payload process(Payload payload) {
        String projectName = payload.getHeader(Payload.PROJECT_NAME);

        Collection<Map<String, Object>> cfg = cfgDao.getList(projectName,
                AnsibleConfigurationConstants.GROUP_KEY, AnsibleConfigurationConstants.PRIVATE_KEYS);
        if (cfg == null) {
            log.debug("process ['{}'] -> configuration not found, nothing to do", payload.getInstanceId());
            return payload;
        }

        String secret = findMatchingSecret(payload, cfg);
        if (secret == null) {
            throw new ProcessException("No matching secrets found");
        }

        KeyPair keyPair = secretManager.getKeyPair(secret);
        if (keyPair.getPrivateKey() == null) {
            throw new ProcessException("Private key not found: " + secret);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path dst = workspace.resolve(AnsibleConstants.PRIVATE_KEY_FILE_NAME);
        if (Files.exists(dst)) {
            throw new ProcessException("File already exists: " + dst);
        }

        try {
            Files.write(dst, keyPair.getPrivateKey());
        } catch (IOException e) {
            throw new ProcessException("Error while copying a private key: " + dst, e);
        }

        return payload;
    }

    private static String findMatchingSecret(Payload payload, Collection<Map<String, Object>> items) {
        RepositoryInfo info = payload.getHeader(RepositoryProcessor.REPOSITORY_INFO_KEY);
        for (Map<String, Object> i : items) {
            String secret = (String) i.get(AnsibleConfigurationConstants.SECRET_KEY);
            if (secret == null || secret.trim().isEmpty()) {
                continue;
            }

            String repo = (String) i.get(AnsibleConfigurationConstants.REPOSITORY_KEY);
            if (repo != null && info.getName().matches(repo)) {
                return secret;
            }
        }
        return null;
    }
}
