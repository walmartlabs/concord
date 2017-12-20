package com.walmartlabs.concord.server.console;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.api.process.*;
import com.walmartlabs.concord.server.process.ConcordFormService;
import com.walmartlabs.concord.server.process.pipelines.processors.RequestInfoProcessor;
import io.takari.bpm.api.ExecutionException;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.*;

import static javax.ws.rs.core.Response.Status;

@Named
public class ProcessPortalServiceImpl implements ProcessPortalService, Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessPortalServiceImpl.class);

    private static final long STATUS_REFRESH_DELAY = 250;

    private final ProcessResource processResource;
    private final ConcordFormService formService;
    private final CustomFormService customFormService;
    private final ResponseTemplates responseTemplates;

    @Inject
    public ProcessPortalServiceImpl(ProcessResource processResource, ConcordFormService formService, CustomFormService customFormService) {
        this.processResource = processResource;
        this.formService = formService;
        this.customFormService = customFormService;
        this.responseTemplates = new ResponseTemplates();
    }

    @Override
    @Validate
    @RequiresAuthentication
    public Response startProcess(String entryPoint, String activeProfiles, UriInfo uriInfo) {
        try {
            return doStartProcess(entryPoint, activeProfiles, uriInfo);
        } catch (Exception e) {
            log.error("startProcess ['{}', '{}'] -> error", entryPoint, activeProfiles, e);
            return processError(null, "Process error: " + e.getMessage());
        }
    }

    private Response doStartProcess(String entryPoint, String activeProfiles, UriInfo uriInfo) {
        if (entryPoint == null || entryPoint.trim().isEmpty()) {
            return badRequest("Entry point is not specified");
        }

        Map<String, Object> req = new HashMap<>();
        if (activeProfiles != null) {
            String[] as = activeProfiles.split(",");
            req.put(InternalConstants.Request.ACTIVE_PROFILES_KEY, Arrays.asList(as));
        }

        if (uriInfo != null) {
            Map<String, Object> args = new HashMap<>();
            args.put("requestInfo", RequestInfoProcessor.createRequestInfo(uriInfo));
            req.put(InternalConstants.Request.ARGUMENTS_KEY, args);
        }

        StartProcessResponse resp;
        try {
            resp = processResource.start(entryPoint, req, null, false, null);
        } catch (Exception e) {
            return processError(null, e.getMessage());
        }

        UUID instanceId = resp.getInstanceId();
        while (true) {
            ProcessEntry psr = processResource.get(instanceId);
            ProcessStatus status = psr.getStatus();

            if (status == ProcessStatus.SUSPENDED) {
                break;
            } else if (status == ProcessStatus.FAILED || status == ProcessStatus.CANCELLED) {
                return processError(instanceId, "Process failed");
            } else if (status == ProcessStatus.FINISHED) {
                return processFinished(instanceId);
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
            return processError(instanceId, "Error while retrieving the list of process forms");
        }

        if (forms == null || forms.isEmpty()) {
            return processError(instanceId, "Invalid process state: no forms found");
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

    private Response processFinished(UUID instanceId) {
        return responseTemplates.processFinished(Response.ok(),
                Collections.singletonMap("instanceId", instanceId))
                .build();
    }

    private Response badRequest(String message) {
        return responseTemplates.badRequest(Response.status(Status.BAD_REQUEST),
                Collections.singletonMap("message", message))
                .build();
    }

    private Response processError(UUID instanceId, String message) {
        Map<String, Object> args = new HashMap<>();
        if (instanceId != null) {
            args.put("instanceId", instanceId);
        }
        args.put("message", message);

        return responseTemplates.processError(Response.status(Status.INTERNAL_SERVER_ERROR), args)
                .build();
    }
}
