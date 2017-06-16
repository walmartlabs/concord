package com.walmartlabs.concord.server.ansible;

import com.walmartlabs.concord.plugins.ansible.AnsibleConstants;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.pipelines.processors.Chain;
import com.walmartlabs.concord.server.process.pipelines.processors.PayloadProcessor;
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

    private final LogManager logManager;
    private final SecretManager secretManager;

    @Inject
    public PrivateKeyProcessor(LogManager logManager, SecretManager secretManager) {
        this.logManager = logManager;
        this.secretManager = secretManager;
    }

    @Override
    @WithTimer
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
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
            logManager.error(payload.getInstanceId(), "No matching secrets found");
            throw new ProcessException("No matching secrets found");
        }

        KeyPair keyPair = secretManager.getKeyPair(secret);
        if (keyPair == null) {
            logManager.error(payload.getInstanceId(), "Secret not found: " + secret);
            throw new ProcessException("Secret not found: " + secret);
        }

        if (keyPair.getPrivateKey() == null) {
            logManager.error(payload.getInstanceId(), "Private key not found: " + secret);
            throw new ProcessException("Private key not found: " + secret);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path dst = workspace.resolve(AnsibleConstants.PRIVATE_KEY_FILE_NAME);
        if (Files.exists(dst)) {
            logManager.error(payload.getInstanceId(), "File already exists: " + dst);
            throw new ProcessException("File already exists: " + dst);
        }

        try {
            Files.write(dst, keyPair.getPrivateKey());
        } catch (IOException e) {
            logManager.error(payload.getInstanceId(), "Error while copying a private key: " + dst, e);
            throw new ProcessException("Error while copying a private key: " + dst, e);
        }

        log.info("process ['{}'] -> done", payload.getInstanceId());
        return chain.process(payload);
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
