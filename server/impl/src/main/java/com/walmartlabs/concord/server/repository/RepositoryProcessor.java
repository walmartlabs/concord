package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.repository.RepositoryEntry;
import com.walmartlabs.concord.server.api.security.secret.SecretType;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.pipelines.processors.PayloadProcessor;
import com.walmartlabs.concord.server.security.PasswordManager;
import com.walmartlabs.concord.server.security.apikey.ApiKey;
import com.walmartlabs.concord.server.security.secret.KeyPair;
import com.walmartlabs.concord.server.security.secret.SecretDao;
import com.walmartlabs.concord.server.security.secret.SecretDao.SecretDataEntry;
import org.eclipse.jgit.api.errors.GitAPIException;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Adds repository files to a payload.
 */
@Named
public class RepositoryProcessor implements PayloadProcessor {

    private final RepositoryDao repositoryDao;
    private final SecretDao secretDao;
    private final SecretStoreConfiguration secretCfg;
    private final PasswordManager passwordManager;

    @Inject
    public RepositoryProcessor(RepositoryDao repositoryDao, SecretDao secretDao,
                               SecretStoreConfiguration secretCfg, PasswordManager passwordManager) {

        this.repositoryDao = repositoryDao;
        this.secretDao = secretDao;
        this.secretCfg = secretCfg;
        this.passwordManager = passwordManager;
    }

    @Override
    public Payload process(Payload payload) {
        String projectId = payload.getHeader(Payload.PROJECT_ID);
        String[] entryPoint = payload.getHeader(Payload.ENTRY_POINT);
        if (projectId == null || entryPoint == null || entryPoint.length < 1) {
            return payload;
        }

        // the name of a repository is always a second part in an entry point, but
        // we extracted project's name earlier
        // TODO remove when the support for default repositories will be implemented?
        String repoName = entryPoint[0];

        RepositoryEntry repo = repositoryDao.getByName(repoName);
        if (repo == null) {
            return payload;
        }

        KeyPair kp = null;
        if (repo.getSecret() != null) {
            SecretDataEntry s = secretDao.get(repo.getSecret().getId());
            if (s.getType() != SecretType.KEY_PAIR) {
                throw new ProcessException("Only key-based authentication is supported, got: " + s.getType());
            }

            // TODO move into security utils?
            byte[] password = passwordManager.getPassword(s.getName(), ApiKey.getCurrentApiKey());
            kp = KeyPair.decrypt(s.getData(), password, secretCfg.getSalt());
        }

        try {
            Path src = GitRepository.checkout(repo.getUrl(), kp);
            Path dst = payload.getHeader(Payload.WORKSPACE_DIR);
            IOUtils.copy(src, dst);
        } catch (IOException | GitAPIException e) {
            throw new ProcessException("Error while pulling a repository: " + repo.getUrl(), e);
        }

        // TODO replace with a queue/stack/linkedlist?
        entryPoint = entryPoint.length > 1 ? Arrays.copyOfRange(entryPoint, 1, entryPoint.length) : new String[0];
        return payload.putHeader(Payload.ENTRY_POINT, entryPoint);
    }
}
