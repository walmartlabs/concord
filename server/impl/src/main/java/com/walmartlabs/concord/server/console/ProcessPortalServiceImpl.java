package com.walmartlabs.concord.server.console;

import com.walmartlabs.concord.server.api.process.*;
import com.walmartlabs.concord.server.process.ConcordFormService;
import io.takari.bpm.api.ExecutionException;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static javax.ws.rs.core.Response.Status;

@Named
public class ProcessPortalServiceImpl implements ProcessPortalService, Resource {

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
    public Response startProcess(String entryPoint) {
        if (entryPoint == null || entryPoint.trim().isEmpty()) {
            throw new WebApplicationException("Invalid entry point", Status.BAD_REQUEST);
        }

        StartProcessResponse resp = processResource.start(entryPoint, Collections.emptyMap());

        String instanceId = resp.getInstanceId();
        while (true) {
            ProcessStatusResponse psr = processResource.get(instanceId);
            ProcessStatus status = psr.getStatus();

            if (status == ProcessStatus.SUSPENDED) {
                break;
            } else if (status == ProcessStatus.FAILED) {
                throw new WebApplicationException("Process error", Status.INTERNAL_SERVER_ERROR);
            } else if (status == ProcessStatus.FINISHED) {
                // TODO redirect to a success page?
                throw new WebApplicationException("Process finished", Status.OK);
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        List<FormListEntry> forms;
        try {
            forms = formService.list(instanceId);
        } catch (ExecutionException e) {
            throw new WebApplicationException("Process error", Status.INTERNAL_SERVER_ERROR);
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
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .build();
    }
}
