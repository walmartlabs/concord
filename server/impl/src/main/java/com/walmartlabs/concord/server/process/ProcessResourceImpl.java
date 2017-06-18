package com.walmartlabs.concord.server.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.agent.AgentManager;
import com.walmartlabs.concord.server.api.process.*;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.PayloadParser.EntryPoint;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLogEntry;
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
import javax.ws.rs.core.MediaType;
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

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;

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

    @Override
    public StartProcessResponse start(
            InputStream in, boolean sync) {
        String instanceId = UUID.randomUUID().toString();

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, getInitiator(), in);
        } catch (IOException e) {
            log.error("start -> error creating a payload: {}", e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        try {
            archivePipeline.process(payload);
        } catch (Exception e) {
            throw err("Error starting process", Status.INTERNAL_SERVER_ERROR, instanceId);
        }

        if (sync) {
            Map<String, Object> args = readArgs(instanceId);
            process(instanceId, args);
        }

        return new StartProcessResponse(instanceId);
    }

    @Override
    public StartProcessResponse start(String entryPoint, Map<String, Object> req, boolean sync) {
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

        try {
            projectPipeline.process(payload);
        } catch (Exception e) {
            throw err("Error starting process", Status.INTERNAL_SERVER_ERROR, instanceId);
        }

        if (sync) {
            Map<String, Object> args = readArgs(instanceId);
            process(instanceId, args);
        }

        return new StartProcessResponse(instanceId);
    }

    @Override
    public StartProcessResponse start(String entryPoint, MultipartInput input, boolean sync) {
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

        try {
            projectPipeline.process(payload);
        } catch (Exception e) {
            throw err("Error starting process", Status.INTERNAL_SERVER_ERROR, instanceId);
        }

        if (sync) {
            Map<String, Object> args = readArgs(instanceId);
            process(instanceId, args);
        }

        return new StartProcessResponse(instanceId);
    }

    @Override
    @Validate
    public StartProcessResponse start(String projectName, InputStream in, boolean sync) {
        String instanceId = UUID.randomUUID().toString();

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, getInitiator(), projectName, in);
        } catch (IOException e) {
            log.error("start ['{}'] -> error creating a payload: {}", projectName, e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        try {
            archivePipeline.process(payload);
        } catch (Exception e) {
            throw err("Error starting process", Status.INTERNAL_SERVER_ERROR, instanceId);
        }

        if (sync) {
            Map<String, Object> args = readArgs(instanceId);
            process(instanceId, args);
        }

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
    @WithTimer
    public Response downloadAttachment(String instanceId, String attachmentName) {
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
    public List<ProcessEntry> list() {
        return queueDao.list();
    }

    @Override
    @Validate
    @WithTimer
    public Response getLog(String instanceId, String range) {
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

        List<ProcessLogEntry> data = logsDao.get(instanceId, start, end);
        // TODO check if the instance actually exists

        if (data.isEmpty()) {
            int actualStart = start != null ? start : 0;
            int actualEnd = end != null ? end : 0;
            return Response.ok()
                .header("Content-Range", "bytes " + actualStart + "-" + actualEnd + "/0")
                .build();
        }

        ProcessLogEntry first = data.get(0);
        int actualStart = first.getStart();

        ProcessLogEntry last = data.get(data.size() - 1);
        int actualEnd = last.getStart() + last.getData().length;

        StreamingOutput out = output -> {
            for (ProcessLogEntry e : data) {
                output.write(e.getData());
            }
        };

        return Response.ok((StreamingOutput) out)
                .header("Content-Range", "bytes " + actualStart + "-" + actualEnd + "/" + (actualEnd + 1))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readArgs(String instanceId) {
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

    private void process(String instanceId, Map<String, Object> params) {
        while (true) {
            ProcessEntry psr = get(instanceId);
            ProcessStatus status = psr.getStatus();

            if (status == ProcessStatus.SUSPENDED) {
                wakeUpProcess(psr, params);
            } else if (status == ProcessStatus.FAILED) {
                throw err("Process error", Status.INTERNAL_SERVER_ERROR, psr);
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
        String instanceId = entry.getInstanceId();

        List<FormListEntry> forms;
        try {
            forms = formService.list(instanceId);
        } catch (ExecutionException e) {
            throw err("Internal error", Status.INTERNAL_SERVER_ERROR, entry);
        }

        if (forms == null || forms.isEmpty()) {
            throw err("Invalid process state: no forms found", Status.INTERNAL_SERVER_ERROR, entry);
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
                    throw err("Form '" + f.getName() + "' submit error: " + error, Status.BAD_REQUEST, entry);
                }
            } catch (ExecutionException e) {
                throw err("Submit form error", Status.INTERNAL_SERVER_ERROR, entry);
            }
        }
    }

    private static WebApplicationException err(String message, Status status, String instanceId) {
        throw new WebApplicationException(message, Response.status(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_TYPE)
                .entity(ImmutableMap.of("instanceId", instanceId))
                .build());
    }

    private static WebApplicationException err(String message, Status status, ProcessEntry entry) {
        throw new WebApplicationException(message, Response.status(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_TYPE)
                .entity(entry)
                .build());
    }
}
