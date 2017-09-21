package com.walmartlabs.concord.server.console;

import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.api.process.*;
import com.walmartlabs.concord.server.process.ConcordFormService;
import com.walmartlabs.concord.server.process.pipelines.processors.RequestInfoProcessor;
import io.takari.bpm.api.ExecutionException;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.*;

import static javax.ws.rs.core.Response.Status;

@Named
public class ProcessPortalServiceImpl implements ProcessPortalService, Resource {

    private static final long STATUS_REFRESH_DELAY = 250;

    private final ProcessResource processResource;
    private final ConcordFormService formService;
    private final CustomFormService customFormService;

    @Inject
    public ProcessPortalServiceImpl(ProcessResource processResource, ConcordFormService formService, CustomFormService customFormService) {
        this.processResource = processResource;
        this.formService = formService;
        this.customFormService = customFormService;
    }

    @Override
    @Validate
    @RequiresAuthentication
    public Response startProcess(String entryPoint, String activeProfiles, UriInfo uriInfo) {
        if (entryPoint == null || entryPoint.trim().isEmpty()) {
            throw new WebApplicationException("Invalid entry point", Status.BAD_REQUEST);
        }

        Map<String, Object> req = new HashMap<>();
        if (activeProfiles != null) {
            String[] as = activeProfiles.split(",");
            req.put(Constants.Request.ACTIVE_PROFILES_KEY, Arrays.asList(as));
        }

        if (uriInfo != null) {
            Map<String, Object> args = new HashMap<>();
            args.put("requestInfo", RequestInfoProcessor.createRequestInfo(uriInfo));
            req.put(Constants.Request.ARGUMENTS_KEY, args);
        }

        StartProcessResponse resp = processResource.start(entryPoint, req, null, false);

        UUID instanceId = resp.getInstanceId();
        while (true) {
            ProcessEntry psr = processResource.get(instanceId);
            ProcessStatus status = psr.getStatus();

            if (status == ProcessStatus.SUSPENDED) {
                break;
            } else if (status == ProcessStatus.FAILED || status == ProcessStatus.CANCELLED) {
                throw new WebApplicationException("Process error", Status.INTERNAL_SERVER_ERROR);
            } else if (status == ProcessStatus.FINISHED) {
                // TODO redirect to a success page?
                return Response.ok(psr, MediaType.APPLICATION_JSON)
                        .build();
            }

            try {
                // TODO exp back off?
                Thread.sleep(STATUS_REFRESH_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        List<FormListEntry> forms;
        try {
            forms = formService.list(instanceId);
        } catch (ExecutionException e) {
            throw new WebApplicationException("Process error", e);
        }

        if (forms == null || forms.isEmpty()) {
            throw new WebApplicationException("Invalid process state: no forms found", Status.INTERNAL_SERVER_ERROR);
        }

        FormListEntry f = forms.get(0);
        if (!f.isCustom()) {
            String dst = "/#/process/" + instanceId + "/form/" + f.getFormInstanceId() + "?fullScreen=true&wizard=true";
            return Response.status(Status.MOVED_PERMANENTLY)
                    .header(HttpHeaders.LOCATION, dst)
                    .build();
        }

        FormSessionResponse fsr = customFormService.startSession(instanceId, f.getFormInstanceId());
        return Response.status(Status.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, fsr.getUri())
                .build();
    }
}
