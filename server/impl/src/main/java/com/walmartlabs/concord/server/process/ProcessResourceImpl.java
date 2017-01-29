package com.walmartlabs.concord.server.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.history.ProcessHistoryEntry;
import com.walmartlabs.concord.server.api.process.*;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.history.ProcessHistoryDao;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.repository.GitRepository;
import com.walmartlabs.concord.server.repository.RepositoryDao;
import com.walmartlabs.concord.server.security.User;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.zip.ZipInputStream;

@Named
public class ProcessResourceImpl implements ProcessResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessResourceImpl.class);

    public static final String ANONYMOUS_PROJECT_KEY = "_anon";

    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final ProcessHistoryDao historyDao;
    private final ProcessExecutor processExecutor;
    private final Executor threadPool;

    @Inject
    public ProcessResourceImpl(ProjectDao projectDao,
                               RepositoryDao repositoryDao,
                               ProcessHistoryDao historyDao,
                               ProcessExecutor processExecutor) {

        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.historyDao = historyDao;
        this.processExecutor = processExecutor;

        // TODO cfg
        this.threadPool = Executors.newCachedThreadPool();
    }

    @Override
    public StartProcessResponse start(InputStream in) {
        String instanceId = UUID.randomUUID().toString();
        Path tmpPath = unpack(in);
        return start(ANONYMOUS_PROJECT_KEY, instanceId, tmpPath);
    }

    @Override
    public StartProcessResponse start(String entryPoint, Map<String, Object> req) {
        // TODO refactor
        String[] as = entryPoint.split(":");
        if (as.length < 1 || as.length > 3) {
            throw new WebApplicationException("Invalid entry point format", Status.BAD_REQUEST);
        }

        String projectName = as[0].trim();
        String repoName = as.length > 1 ? as[1].trim() : null;
        String realEntryPoint = as.length > 2 ? as[2].trim() : null;

        String instanceId = UUID.randomUUID().toString();
        Path payloadPath = null;

        // if an entry point contains a name of a repository, we need to fetch our payload first
        if (repoName != null) {
            String url = repositoryDao.findUrl(projectName, repoName);
            if (url == null) {
                throw new WebApplicationException("Repository URL not found: " + entryPoint, Status.BAD_REQUEST);
            }

            Path tmpPath;
            try {
                tmpPath = GitRepository.checkout(url);
                payloadPath = tmpPath;
            } catch (IOException | GitAPIException e) {
                log.error("start ['{}'] -> error while cloning a repository", instanceId, e);
                throw new WebApplicationException("Error while cloning a repository: " + e.getMessage(), e);
            }
        }

        // running in a standalone mode (e.g. without a repository)
        if (payloadPath == null) {
            // TODO cfg?
            try {
                payloadPath = Files.createTempDirectory("payload");
            } catch (IOException e) {
                throw new WebApplicationException(e);
            }
        }

        createMeta(payloadPath.resolve(Constants.METADATA_FILE_NAME), realEntryPoint, req);

        return start(projectName, instanceId, payloadPath);
    }

    private StartProcessResponse start(String projectName, String instanceId, Path data) {
        Subject subject = SecurityUtils.getSubject();
        User user = (User) subject.getPrincipal();

        if (!subject.isPermitted(String.format(Permissions.PROCESS_START_PROJECT, projectName))) {
            log.warn("start ['{}'] -> forbidden", instanceId);
            throw new WebApplicationException("The current user does not have permissions to start this project", Status.FORBIDDEN);
        }

        String projectId = projectDao.getId(projectName);
        String initiator = user.getName();
        String logFileName = instanceId + ".log";

        ProcessExecutorCallback callback = (payload, status) -> historyDao.update(payload.getInstanceId(), status);

        historyDao.insertInitial(instanceId, initiator, logFileName);

        threadPool.execute(() -> {
            try (Payload p = new Payload(instanceId, projectId, initiator, logFileName, data)) {
                processExecutor.run(p, callback);
            } catch (ProcessExecutorException e) {
                throw new WebApplicationException(e);
            }
        });

        return new StartProcessResponse(instanceId);
    }

    @Override
    public ProcessStatusResponse waitForCompletion(@PathParam("id") String instanceId, @QueryParam("timeout") @DefaultValue("-1") long timeout) {
        log.info("waitForCompletion ['{}', {}] -> waiting...", instanceId, timeout);

        long t1 = System.currentTimeMillis();

        ProcessStatusResponse r;
        while (true) {
            r = get(instanceId);
            if (r.getStatus() == ProcessStatus.FINISHED || r.getStatus() == ProcessStatus.FAILED) {
                break;
            }

            if (timeout > 0) {
                long t2 = System.currentTimeMillis();
                if (t2 - t1 >= timeout) {
                    log.warn("waitForCompletion ['{}', {}] -> timeout, last status: {}", instanceId, timeout, r.getStatus());
                    throw new WebApplicationException(Response.status(Status.REQUEST_TIMEOUT).entity(r).build());
                }
            }

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                break;
            }
        }
        return r;
    }

    @Override
    public void kill(String agentId) {
        processExecutor.cancel(agentId);
    }

    @Override
    public ProcessStatusResponse get(String instanceId) {
        ProcessHistoryEntry r = historyDao.get(instanceId);
        if (r == null) {
            log.warn("get ['{}'] -> not found", instanceId);
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return new ProcessStatusResponse(r.getLastChangeDt(), r.getStatus(), r.getLogFileName());
    }

    private static Path unpack(InputStream in) {
        // TODO validate payload
        // TODO cfg
        try {
            Path p = Files.createTempDirectory("payload");
            try (ZipInputStream zip = new ZipInputStream(in)) {
                IOUtils.unzip(zip, p);
            }
            return p;
        } catch (IOException e) {
            log.error("unpack -> error unpacking the payload", e);
            throw new WebApplicationException(e);
        }
    }

    private static void createMeta(Path path, String entryPoint, Map<String, Object> args) {
        // TODO constants
        Map<String, Object> meta = new HashMap<>();
        if (args != null) {
            meta.putAll(args);
        }
        if (entryPoint != null) {
            meta.put("entryPoint", entryPoint);
        }
        ObjectMapper om = new ObjectMapper();
        try {
            om.writeValue(path.toFile(), meta);
        } catch (IOException e) {
            throw new WebApplicationException(e);
        }
    }
}
