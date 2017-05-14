package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.agent.AgentManager;
import com.walmartlabs.concord.server.api.process.*;
import com.walmartlabs.concord.server.process.PayloadParser.EntryPoint;
import com.walmartlabs.concord.server.process.pipelines.ArchivePipeline;
import com.walmartlabs.concord.server.process.pipelines.ProjectPipeline;
import com.walmartlabs.concord.server.process.pipelines.ResumePipeline;
import com.walmartlabs.concord.server.process.pipelines.processors.Chain;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Named
public class ProcessResourceImpl implements ProcessResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessResourceImpl.class);

    private final ProjectDao projectDao;
    private final ProcessQueueDao queueDao;
    private final Chain archivePipeline;
    private final Chain projectPipeline;
    private final Chain resumePipeline;
    private final PayloadManager payloadManager;
    private final AgentManager agentManager;

    @Inject
    public ProcessResourceImpl(ProjectDao projectDao,
                               ProcessQueueDao queueDao,
                               ArchivePipeline archivePipeline,
                               ProjectPipeline projectPipeline,
                               ResumePipeline resumePipeline,
                               PayloadManager payloadManager,
                               AgentManager agentManager) {

        this.projectDao = projectDao;
        this.queueDao = queueDao;
        this.archivePipeline = archivePipeline;
        this.projectPipeline = projectPipeline;
        this.resumePipeline = resumePipeline;
        this.payloadManager = payloadManager;
        this.agentManager = agentManager;
    }

    @Override
    public StartProcessResponse start(InputStream in) {
        String instanceId = UUID.randomUUID().toString();

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, getInitiator(), in);
        } catch (IOException e) {
            log.error("start -> error creating a payload: {}", e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        archivePipeline.process(payload);
        return new StartProcessResponse(instanceId);
    }

    @Override
    public StartProcessResponse start(String entryPoint, Map<String, Object> req) {
        String instanceId = UUID.randomUUID().toString();

        EntryPoint ep = PayloadParser.parseEntryPoint(entryPoint);
        assertProject(ep.getProjectName());

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, getInitiator(), ep, req);
        } catch (IOException e) {
            log.error("start ['{}'] -> error creating a payload: {}", entryPoint, e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        projectPipeline.process(payload);
        return new StartProcessResponse(instanceId);
    }

    @Override
    public StartProcessResponse start(String entryPoint, MultipartInput input) {
        String instanceId = UUID.randomUUID().toString();

        EntryPoint ep = PayloadParser.parseEntryPoint(entryPoint);
        assertProject(ep.getProjectName());

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, getInitiator(), ep, input);
        } catch (IOException e) {
            log.error("start ['{}'] -> error creating a payload: {}", entryPoint, e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        projectPipeline.process(payload);
        return new StartProcessResponse(instanceId);
    }

    @Override
    @Validate
    public StartProcessResponse start(String projectName, InputStream in) {
        String instanceId = UUID.randomUUID().toString();

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, getInitiator(), projectName, in);
        } catch (IOException e) {
            log.error("start ['{}'] -> error creating a payload: {}", projectName, e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        archivePipeline.process(payload);
        return new StartProcessResponse(instanceId);
    }

    @Override
    @Validate
    public ResumeProcessResponse resume(String instanceId, String eventName, Map<String, Object> req) {
        Payload payload;
        try {
            payload = payloadManager.createResumePayload(instanceId, eventName, req);
        } catch (IOException e) {
            log.error("resume ['{}', '{}'] -> error creating a payload: {}", instanceId, eventName, e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        resumePipeline.process(payload);
        return new ResumeProcessResponse();
    }

    private void assertProject(String projectName) {
        if (!projectDao.exists(projectName)) {
            throw new ValidationErrorsException("Unknown project name: " + projectName);
        }
    }

    @Override
    @Validate
    public ProcessEntry waitForCompletion(String instanceId, long timeout) {
        log.info("waitForCompletion ['{}', {}] -> waiting...", instanceId, timeout);

        long t1 = System.currentTimeMillis();

        ProcessEntry r;
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
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
        return r;
    }

    @Override
    @Validate
    public void kill(String instanceId) {
        ProcessEntry entry = queueDao.get(instanceId);
        if (entry == null) {
            throw new WebApplicationException("Process not found: " + instanceId, Status.NOT_FOUND);
        }

        boolean stopped = false;
        if (entry.getStatus() == ProcessStatus.SUSPENDED) {
            stopped = queueDao.update(instanceId, ProcessStatus.SUSPENDED, ProcessStatus.FAILED);
        }

        if (!stopped) {
            agentManager.killProcess(instanceId);
        }
    }

    @Override
    @Validate
    public ProcessEntry get(String instanceId) {
        ProcessEntry e = queueDao.get(instanceId);
        if (e == null) {
            log.warn("get ['{}'] -> not found", instanceId);
            throw new WebApplicationException("Process instance not found", Status.NOT_FOUND);
        }
        return new ProcessEntry(instanceId, e.getProjectName(), e.getCreatedAt(), e.getInitiator(),
                e.getLastUpdatedAt(), e.getStatus(), e.getLastAgentId());
    }

    @Override
    @Validate
    public Response downloadAttachment(String instanceId, String attachmentName) {
        if (attachmentName.endsWith("/")) {
            throw new WebApplicationException("Invalid attachment name: " + attachmentName, Status.BAD_REQUEST);
        }

        // TODO sanitize
        Path p = payloadManager.getResource(instanceId, Constants.Files.JOB_ATTACHMENTS_DIR_NAME + "/" + attachmentName);
        if (p == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        return Response.ok((StreamingOutput) out -> {
            try (InputStream in = Files.newInputStream(p)) {
                IOUtils.copy(in, out);
            }
        }).build();
    }

    private static String getInitiator() {
        Subject subject = SecurityUtils.getSubject();
        if (subject == null || !subject.isAuthenticated()) {
            return null;
        }

        UserPrincipal u = (UserPrincipal) subject.getPrincipal();
        return u.getUsername();
    }

    @Override
    public List<ProcessEntry> list() {
        return queueDao.list();
    }
}
