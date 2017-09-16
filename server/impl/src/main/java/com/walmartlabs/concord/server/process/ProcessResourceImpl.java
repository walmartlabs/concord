package com.walmartlabs.concord.server.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.agent.AgentManager;
import com.walmartlabs.concord.server.api.process.*;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.PayloadParser.EntryPoint;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLog;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLogChunk;
import com.walmartlabs.concord.server.process.pipelines.ArchivePipeline;
import com.walmartlabs.concord.server.process.pipelines.ProjectPipeline;
import com.walmartlabs.concord.server.process.pipelines.ResumePipeline;
import com.walmartlabs.concord.server.process.pipelines.processors.Chain;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.security.UserPrincipal;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.form.FormSubmitResult;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
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
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;
import static com.walmartlabs.concord.server.process.state.ProcessStateManager.zipTo;

@Named
public class ProcessResourceImpl implements ProcessResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessResourceImpl.class);

    private final ProjectDao projectDao;
    private final ProcessQueueDao queueDao;
    private final ProcessLogsDao logsDao;
    private final Chain archivePipeline;
    private final Chain projectPipeline;
    private final Chain resumePipeline;
    private final PayloadManager payloadManager;
    private final ProcessStateManager stateManager;
    private final AgentManager agentManager;
    private final ConcordFormService formService;

    @Inject
    public ProcessResourceImpl(ProjectDao projectDao,
                               ProcessQueueDao queueDao,
                               ProcessLogsDao logsDao, ArchivePipeline archivePipeline,
                               ProjectPipeline projectPipeline,
                               ResumePipeline resumePipeline,
                               PayloadManager payloadManager,
                               ProcessStateManager stateManager,
                               AgentManager agentManager,
                               ConcordFormService concordFormService) {

        this.projectDao = projectDao;
        this.queueDao = queueDao;
        this.logsDao = logsDao;
        this.archivePipeline = archivePipeline;
        this.projectPipeline = projectPipeline;
        this.resumePipeline = resumePipeline;
        this.payloadManager = payloadManager;
        this.stateManager = stateManager;
        this.agentManager = agentManager;
        this.formService = concordFormService;
    }

    private StartProcessResponse start(Chain pipeline, Payload payload, boolean sync) {
        UUID instanceId = payload.getInstanceId();

        try {
            pipeline.process(payload);
        } catch (ProcessException e) {
            throw e;
        } catch (Exception e) {
            log.error("start ['{}'] -> error starting the process", instanceId, e);
            throw new ProcessException(instanceId, "Error starting the process", e, Status.INTERNAL_SERVER_ERROR);
        }

        if (sync) {
            Map<String, Object> args = readArgs(instanceId);
            process(instanceId, args);
        }

        return new StartProcessResponse(instanceId);
    }

    @Override
    @RequiresAuthentication
    public StartProcessResponse start(InputStream in, boolean sync) {
        UUID instanceId = UUID.randomUUID();

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, getInitiator(), in);
        } catch (IOException e) {
            log.error("start -> error creating a payload: {}", e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        return start(archivePipeline, payload, sync);
    }

    @Override
    public StartProcessResponse start(String entryPoint, boolean sync) {
        return start(entryPoint, Collections.emptyMap(), sync);
    }

    @Override
    @RequiresAuthentication
    public StartProcessResponse start(String entryPoint, Map<String, Object> req, boolean sync) {
        UUID instanceId = UUID.randomUUID();

        EntryPoint ep = PayloadParser.parseEntryPoint(entryPoint);
        assertProject(ep.getProjectName());

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, getInitiator(), ep, req);
        } catch (IOException e) {
            log.error("start ['{}'] -> error creating a payload: {}", entryPoint, e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        return start(projectPipeline, payload, sync);
    }

    @Override
    @RequiresAuthentication
    public StartProcessResponse start(MultipartInput input, boolean sync) {
        return start(null, input, sync);
    }

    @Override
    @RequiresAuthentication
    public StartProcessResponse start(String entryPoint, MultipartInput input, boolean sync) {
        UUID instanceId = UUID.randomUUID();

        EntryPoint ep = PayloadParser.parseEntryPoint(entryPoint);
        if (ep != null) {
            assertProject(ep.getProjectName());
        }

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, getInitiator(), ep, input);
        } catch (IOException e) {
            log.error("start ['{}'] -> error creating a payload: {}", entryPoint, e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        return start(projectPipeline, payload, sync);
    }

    @Override
    @Validate
    @RequiresAuthentication
    public StartProcessResponse start(String projectName, InputStream in, boolean sync) {
        UUID instanceId = UUID.randomUUID();

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, getInitiator(), projectName, in);
        } catch (IOException e) {
            log.error("start ['{}'] -> error creating a payload: {}", projectName, e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        return start(archivePipeline, payload, sync);
    }

    @Override
    @Validate
    @RequiresAuthentication
    public ResumeProcessResponse resume(UUID instanceId, String eventName, Map<String, Object> req) {
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
    @RequiresAuthentication
    public ProcessEntry waitForCompletion(UUID instanceId, long timeout) {
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
    @RequiresAuthentication
    public void kill(UUID instanceId) {
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
    @RequiresAuthentication
    public ProcessEntry get(UUID instanceId) {
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
    @WithTimer
    @RequiresAuthentication
    public Response downloadAttachment(UUID instanceId, String attachmentName) {
        // TODO replace with javax.validation
        if (attachmentName.endsWith("/")) {
            throw new WebApplicationException("Invalid attachment name: " + attachmentName, Status.BAD_REQUEST);
        }

        String resource = path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME, attachmentName);
        Optional<Path> o = stateManager.get(instanceId, resource, src -> {
            try {
                Path tmp = Files.createTempFile("attachment", ".bin");
                try (OutputStream dst = Files.newOutputStream(tmp)) {
                    IOUtils.copy(src, dst);
                }
                return Optional.of(tmp);
            } catch (IOException e) {
                throw new WebApplicationException("Error while exporting an attachment: " + attachmentName, e);
            }
        });

        if (!o.isPresent()) {
            return Response.status(Status.NOT_FOUND).build();
        }

        Path tmp = o.get();

        return Response.ok((StreamingOutput) out -> {
            try (InputStream in = Files.newInputStream(tmp)) {
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
    @WithTimer
    @RequiresAuthentication
    public List<ProcessEntry> list() {
        return queueDao.list();
    }

    @Override
    @Validate
    @WithTimer
    @RequiresAuthentication
    public Response getLog(UUID instanceId, String range) {
        Integer start = null;
        Integer end = null;

        if (range != null && !range.trim().isEmpty()) {
            if (!range.startsWith("bytes=")) {
                throw new WebApplicationException("Invalid range header: " + range, Status.BAD_REQUEST);
            }

            String[] as = range.substring("bytes=".length()).split("-");
            if (as.length > 0) {
                try {
                    start = Integer.parseInt(as[0]);
                } catch (NumberFormatException e) {
                }
            }

            if (as.length > 1) {
                try {
                    end = Integer.parseInt(as[1]);
                } catch (NumberFormatException e) {
                }
            }
        }

        ProcessLog l = logsDao.get(instanceId, start, end);
        List<ProcessLogChunk> data = l.getChunks();
        // TODO check if the instance actually exists

        if (data.isEmpty()) {
            int actualStart = start != null ? start : 0;
            int actualEnd = end != null ? end : start;
            return Response.ok()
                    .header("Content-Range", "bytes " + actualStart + "-" + actualEnd + "/" + l.getSize())
                    .build();
        }

        ProcessLogChunk first = data.get(0);
        int actualStart = first.getStart();

        ProcessLogChunk last = data.get(data.size() - 1);
        int actualEnd = last.getStart() + last.getData().length;

        StreamingOutput out = output -> {
            for (ProcessLogChunk e : data) {
                output.write(e.getData());
            }
        };

        return Response.ok(out)
                .header("Content-Range", "bytes " + actualStart + "-" + actualEnd + "/" + l.getSize())
                .build();
    }

    @Override
    @Validate
    @WithTimer
    @RequiresAuthentication
    public Response downloadState(UUID instanceId) {
        if (!stateManager.exists(instanceId)) {
            throw new WebApplicationException("Process instance not found", Status.NOT_FOUND);
        }

        StreamingOutput out = output -> {
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                stateManager.export(instanceId, zipTo(zip));
            }
        };

        return Response.ok(out)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + instanceId + ".zip\"")
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readArgs(UUID instanceId) {
        String resource = Constants.Files.REQUEST_DATA_FILE_NAME;
        Optional<Map<String, Object>> o = stateManager.get(instanceId, resource, in -> {
            try {
                ObjectMapper om = new ObjectMapper();

                Map<String, Object> cfg = om.readValue(in, Map.class);
                Map<String, Object> args = (Map<String, Object>) cfg.get(Constants.Request.ARGUMENTS_KEY);

                return Optional.ofNullable(args);
            } catch (IOException e) {
                throw new WebApplicationException("Error while reading request data", e);
            }
        });
        return o.orElse(Collections.emptyMap());
    }

    private void process(UUID instanceId, Map<String, Object> params) {
        while (true) {
            ProcessEntry psr = get(instanceId);
            ProcessStatus status = psr.getStatus();

            if (status == ProcessStatus.SUSPENDED) {
                wakeUpProcess(psr, params);
            } else if (status == ProcessStatus.FAILED) {
                throw new ProcessException(instanceId, "Process error", Status.INTERNAL_SERVER_ERROR);
            } else if (status == ProcessStatus.FINISHED) {
                return;
            }

            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void wakeUpProcess(ProcessEntry entry, Map<String, Object> data) {
        UUID instanceId = entry.getInstanceId();

        List<FormListEntry> forms;
        try {
            forms = formService.list(instanceId);
        } catch (ExecutionException e) {
            throw new ProcessException(instanceId, "Process execution error", e);
        }

        if (forms == null || forms.isEmpty()) {
            throw new ProcessException(instanceId, "Invalid process state: no forms found");
        }

        for (FormListEntry f : forms) {
            try {
                Map<String, Object> args = (Map<String, Object>) data.get(f.getName());

                FormSubmitResult submitResult = formService.submit(instanceId, f.getFormInstanceId(), args);
                if (!submitResult.isValid()) {
                    String error = "n/a";
                    if (submitResult.getErrors() != null) {
                        error = submitResult.getErrors().stream().map(e -> e.getFieldName() + ": " + e.getError()).collect(Collectors.joining(","));
                    }
                    throw new ProcessException(instanceId, "Form '" + f.getName() + "' submit error: " + error, Status.BAD_REQUEST);
                }
            } catch (ExecutionException e) {
                throw new ProcessException(instanceId, "Form submit error: " + e.getMessage(), e);
            }
        }
    }
}
