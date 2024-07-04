package com.walmartlabs.concord.server.plugins.ansible.queue;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.pipelines.processors.RepositoryProcessor;
import com.walmartlabs.concord.server.process.pipelines.processors.RepositoryProcessor.RepositoryInfo;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.process.CustomEnqueueProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Deprecated
public class PrivateKeyProcessor implements CustomEnqueueProcessor {

    private static final Logger log = LoggerFactory.getLogger(PrivateKeyProcessor.class);

    private static final String PRIVATE_KEY_FILE_NAME = "_privateKey";

    private final ProcessLogManager logManager;
    private final SecretManager secretManager;
    private final ProjectDao projectDao;

    @Inject
    public PrivateKeyProcessor(ProcessLogManager logManager, SecretManager secretManager, ProjectDao projectDao) {
        this.logManager = logManager;
        this.secretManager = secretManager;
        this.projectDao = projectDao;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Payload handleState(Payload payload) {
        ProcessKey processKey = payload.getProcessKey();
        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);

        Map<String, Object> ansibleCfg = (Map<String, Object>) cfg.get(AnsibleConfigurationConstants.GROUP_KEY);
        if (ansibleCfg == null) {
            return payload;
        }

        Collection<Map<String, Object>> keys = (Collection<Map<String, Object>>) ansibleCfg.get(AnsibleConfigurationConstants.PRIVATE_KEYS);
        if (keys == null) {
            return payload;
        }

        deprecationWarning(processKey);

        String secret = findMatchingSecret(payload, keys);
        if (secret == null) {
            logManager.error(processKey, "No matching secrets found");
            throw new ProcessException(processKey, "No matching secrets found");
        }

        UUID orgId = getOrgId(payload);
        KeyPair keyPair = secretManager.getKeyPair(SecretManager.AccessScope.internal(), orgId, secret, null);
        if (keyPair == null) {
            logManager.error(processKey, "Secret not found: " + secret);
            throw new ProcessException(processKey, "Secret not found: " + secret);
        }

        if (keyPair.getPrivateKey() == null) {
            logManager.error(processKey, "Private key not found: " + secret);
            throw new ProcessException(processKey, "Private key not found: " + secret);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path dst = workspace.resolve(PRIVATE_KEY_FILE_NAME);

        try {
            Files.write(dst, keyPair.getPrivateKey(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            logManager.error(processKey, "Error while copying a private key: " + dst, e);
            throw new ProcessException(processKey, "Error while copying a private key: " + dst, e);
        }

        log.info("process ['{}'] -> done", processKey);
        return payload;
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

    private void deprecationWarning(ProcessKey processKey) {
        String msg = ".. WARNING ............................................................................\n" +
                " 'configuration.ansible.privateKeys' is deprecated.\n" +
                " Please use 'privateKey' parameter of the Ansible task.\n" +
                ".......................................................................................\n";
        logManager.log(processKey, msg);
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
