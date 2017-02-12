package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.pipelines.processors.PayloadProcessor;
import com.walmartlabs.concord.server.security.PasswordManager;
import com.walmartlabs.concord.server.security.apikey.ApiKey;
import com.walmartlabs.concord.server.security.secret.*;
import com.walmartlabs.concord.server.security.secret.SecretDao.SecretDataEntry;
import org.eclipse.jgit.api.errors.GitAPIException;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Function;

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

        RepositoryEntry repo = repositoryDao.getByNameInProject(projectId, repoName);
        if (repo == null) {
            return payload;
        }

        Secret secret = null;
        if (repo.getSecret() != null) {
            secret = getSecret(repo.getSecret().getId());
        }

        try {
            Path src = GitRepository.checkout(repo.getUrl(), repo.getBranch(), secret);
            Path dst = payload.getHeader(Payload.WORKSPACE_DIR);
            IOUtils.copy(src, dst);
        } catch (IOException | GitAPIException e) {
            throw new ProcessException("Error while pulling a repository: " + repo.getUrl(), e);
        }

        // TODO replace with a queue/stack/linkedlist?
        entryPoint = entryPoint.length > 1 ? Arrays.copyOfRange(entryPoint, 1, entryPoint.length) : new String[0];
        return payload.putHeader(Payload.ENTRY_POINT, entryPoint);
    }

    private Secret getSecret(String id) {
        SecretDataEntry s = secretDao.get(id);
        if (s == null) {
            throw new ProcessException("Inconsistent data, can't find a secret: " + id);
        }

        Function<byte[], ? extends Secret> deserializer;
        switch (s.getType()) {
            case KEY_PAIR:
                deserializer = KeyPair::deserialize;
                break;
            case USERNAME_PASSWORD:
                deserializer = UsernamePassword::deserialize;
                break;
            default:
                throw new ProcessException("Unknown secret type: " + s.getType());
        }

        byte[] password = passwordManager.getPassword(s.getName(), ApiKey.getCurrentKey());
        return SecretUtils.decrypt(deserializer, s.getData(), password, secretCfg.getSalt());
    }
}
