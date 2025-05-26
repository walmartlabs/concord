package com.walmartlabs.concord.server.console;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.walmartlabs.concord.forms.Form;
import com.walmartlabs.concord.forms.FormField;
import com.walmartlabs.concord.forms.FormUtils;
import com.walmartlabs.concord.forms.ValidationError;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.cfg.CustomFormConfiguration;
import com.walmartlabs.concord.server.process.form.FormServiceV1;
import com.walmartlabs.concord.server.process.form.FormServiceV2;
import com.walmartlabs.concord.server.process.form.FormSubmitResult;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.*;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.console.CustomFormServiceV1.FormData;
import static com.walmartlabs.concord.server.console.CustomFormServiceV1.FormDataDefinition;
import static com.walmartlabs.concord.server.process.state.ProcessStateManager.copyTo;

public class CustomFormServiceV2 {

    private static final Logger log = LoggerFactory.getLogger(CustomFormServiceV2.class);

    private static final String FORMS_PATH_PREFIX = "/forms/";
    private static final String FORM_DIR_NAME = "form";
    private static final String SHARED_DIR_NAME = "shared";
    private static final String FORMS_PATH_TEMPLATE = FORMS_PATH_PREFIX + "%s/%s/" + FORM_DIR_NAME + "/";
    private static final String DATA_FILE_TEMPLATE = "data = %s;";

    private static final String NON_BRANDED_FORM_URL_TEMPLATE = "/#/process/%s/form/%s/wizard?fullScreen=true";
    private static final String FORM_WIZARD_CONTINUE_URL_TEMPLATE = "/api/service/custom_form/%s/%s/continue";

    private static final long STATUS_REFRESH_DELAY = 250;

    private final CustomFormConfiguration cfg;
    private final FormServiceV2 formService;
    private final ProcessStateManager stateManager;
    private final ProcessQueueDao queueDao;
    private final ProcessKeyCache processKeyCache;
    private final ObjectMapper objectMapper;

    @Inject
    public CustomFormServiceV2(CustomFormConfiguration cfg,
                               FormServiceV2 formService,
                               ProcessStateManager stateManager,
                               ProcessQueueDao queueDao,
                               ProcessKeyCache processKeyCache) {

        this.cfg = cfg;
        this.formService = formService;
        this.stateManager = stateManager;
        this.queueDao = queueDao;
        this.processKeyCache = processKeyCache;

        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    @POST
    @javax.ws.rs.Path("{processInstanceId}/{formName}/start")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public FormSessionResponse startSession(@PathParam("processInstanceId") UUID processInstanceId,
                                            @PathParam("formName") String formName) {

        ProcessKey processKey = processKeyCache.assertKey(processInstanceId);
        return startSession(processKey, formName);
    }

    private FormSessionResponse startSession(ProcessKey processKey, String formName) {
        // TODO locking
        Form form = assertForm(processKey, formName);

        Path dst = cfg.getBaseDir()
                .resolve(processKey.toString())
                .resolve(formName);

        try {
            Path formDir = dst.resolve(FORM_DIR_NAME);
            if (!Files.exists(formDir)) {
                Files.createDirectories(formDir);
            }

            String resource = FormServiceV1.FORMS_RESOURCES_PATH + "/" + form.name();
            // copy original branding files into the target directory
            boolean branded = stateManager.exportDirectory(processKey, resource, copyTo(formDir));
            if (!branded) {
                // not branded, redirect to the default wizard
                String uri = String.format(NON_BRANDED_FORM_URL_TEMPLATE, processKey, formName);
                return new FormSessionResponse(uri);
            }

            // create JS file containing the form's data
            writeData(formDir, initialData(form, processKey.getInstanceId()));

            // copy shared resources (if present)
            copySharedResources(processKey, dst);
        } catch (IOException e) {
            log.warn("startSession ['{}', '{}'] -> error while preparing a custom form: {}", processKey, formName, e);
            throw new ConcordApplicationException("Error while preparing a custom form", e);
        }

        return new FormSessionResponse(formPath(processKey, formName));
    }

    @POST
    @javax.ws.rs.Path("{processInstanceId}/{formName}/continue")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public Response continueSession(@Context UriInfo uriInfo,
                                    @Context HttpHeaders headers,
                                    @PathParam("processInstanceId") UUID processInstanceId,
                                    @PathParam("formName") String formName,
                                    MultivaluedMap<String, String> data) {

        ProcessKey processKey = processKeyCache.assertKey(processInstanceId);
        return continueSession(uriInfo, headers, processKey, formName, com.walmartlabs.concord.server.process.form.FormUtils.convert(data));
    }

    @POST
    @javax.ws.rs.Path("{processInstanceId}/{formName}/continue")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response continueSession(@Context UriInfo uriInfo,
                                    @Context HttpHeaders headers,
                                    @PathParam("processInstanceId") UUID processInstanceId,
                                    @PathParam("formName") String formName,
                                    MultipartInput data) {

        try {
            ProcessKey processKey = processKeyCache.assertKey(processInstanceId);
            return continueSession(uriInfo, headers, processKey, formName, MultipartUtils.toMap(data));
        } finally {
            data.close();
        }
    }

    private Response continueSession(UriInfo uriInfo, HttpHeaders headers, ProcessKey processKey,
                                     String formName, Map<String, Object> data) {
        // TODO locking
        Form form = assertForm(processKey, formName);

        boolean yield = form.options().isYield();

        Path dst = cfg.getBaseDir()
                .resolve(processKey.toString())
                .resolve(formName);

        Path formDir = dst.resolve(FORM_DIR_NAME);

        try {
            Map<String, Object> m = new HashMap<>();
            try {
                m = formService.convertData(processKey, formName, data);

                FormSubmitResult r = formService.submit(processKey, formName, m);
                if (r.isValid()) {
                    if (yield) {
                        // this was the last "interactive" form. The process will continue in "background"
                        // and users should get a success page.
                        writeData(formDir, success(form, m, processKey.getInstanceId()));
                    } else {
                        while (true) {
                            ProcessStatus s = queueDao.getStatus(processKey);

                            if (s == ProcessStatus.SUSPENDED) {
                                String nextFormId = formService.nextFormId(processKey);
                                if (nextFormId == null) {
                                    writeData(formDir, success(form, m, processKey.getInstanceId()));
                                    break;
                                } else {
                                    FormSessionResponse nextSession = startSession(processKey, nextFormId);
                                    return redirectTo(nextSession.getUri());
                                }
                            } else if (s == ProcessStatus.FAILED || s == ProcessStatus.CANCELLED || s == ProcessStatus.TIMED_OUT) {
                                writeData(formDir, processFailed(form, m, processKey.getInstanceId()));
                                break;
                            } else if (s == ProcessStatus.FINISHED) {
                                writeData(formDir, success(form, m, processKey.getInstanceId()));
                                break;
                            }

                            try {
                                // TODO exp back off?
                                Thread.sleep(STATUS_REFRESH_DELAY);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                } else {
                    writeData(formDir, prepareData(form, m, r.getErrors(), processKey.getInstanceId()));
                }
            } catch (FormUtils.ValidationException e) {
                ValidationError err = ValidationError.of(e.getField().name(), e.getMessage());
                FormData d = prepareData(form, m, Collections.singletonList(err), processKey.getInstanceId());
                writeData(formDir, d);
            }
        } catch (Exception e) {
            throw new ConcordApplicationException("Error while submitting a form", e);
        }

        return redirectToForm(uriInfo, headers, processKey, formName);
    }

    private Form assertForm(ProcessKey processKey, String formName) {
        Form form = formService.get(processKey, formName);
        if (form == null) {
            log.warn("assertForm ['{}', '{}'] -> not found", processKey, formName);
            throw new ConcordApplicationException("Form not found", Status.NOT_FOUND);
        }
        return form;
    }

    private FormData initialData(Form form, UUID processInstanceId) {
        return prepareData(false, false, form, null, false, null, processInstanceId);
    }

    private FormData success(Form form, Map<String, Object> overrides, UUID processInstanceId) {
        return prepareData(true, false, form, overrides, false, null, processInstanceId);
    }

    private FormData processFailed(Form form, Map<String, Object> overrides, UUID processInstanceId) {
        return prepareData(false, true, form, overrides, false, null, processInstanceId);
    }

    private FormData prepareData(Form form, Map<String, Object> overrides, List<ValidationError> errors, UUID processInstanceId) {
        return prepareData(false, false, form, overrides, true, errors, processInstanceId);
    }


    private FormData prepareData(boolean success, boolean processFailed, Form form,
                                 Map<String, Object> overrides, boolean skipMissingOverrides,
                                 List<ValidationError> errors,
                                 UUID processInstanceId) {

        // TODO merge with FormResource
        Map<String, FormDataDefinition> _definitions = new HashMap<>();

        // the order of precedence should be:
        //   submitted value > form call value > field's default value > environment value
        Map<String, Object> _values = new HashMap<>(form.options().extraValues());
        Map<String, String> _errors = new HashMap<>();

        form.fields().stream().filter(f -> f.defaultValue() != null)
                .forEach(f -> _values.put(f.name(), f.defaultValue()));

        List<String> fields = form.fields().stream()
                .map(FormField::name)
                .collect(Collectors.toList());

        for (FormField f : form.fields()) {
            Object allowedValue = f.allowedValue();

            _definitions.put(f.name(), new FormDataDefinition(f.label(), f.type(), convert(f.cardinality()), allowedValue));

            Object v = overrides != null ? overrides.get(f.name()) : null;
            if (v == null && skipMissingOverrides) {
                continue;
            }

            if (v == null) {
                continue;
            }

            _values.put(f.name(), v);
        }

        if (errors != null) {
            for (ValidationError e : errors) {
                _errors.put(e.fieldName(), e.error());
            }
        }

        String submitUrl = String.format(FORM_WIZARD_CONTINUE_URL_TEMPLATE, processInstanceId, form.name());

        return FormData.builder()
                .txId(processInstanceId.toString())
                .formName(form.name())
                .success(success)
                .processFailed(processFailed)
                .submitUrl(submitUrl)
                .fields(fields)
                .definitions(_definitions)
                .values(_values)
                .errors(_errors)
                .build();
    }

    private io.takari.bpm.model.form.FormField.Cardinality convert(FormField.Cardinality c) {
        if (c == null) {
            return null;
        }

        switch (c) {
            case ANY:
                return io.takari.bpm.model.form.FormField.Cardinality.ANY;
            case AT_LEAST_ONE:
                return io.takari.bpm.model.form.FormField.Cardinality.AT_LEAST_ONE;
            case ONE_AND_ONLY_ONE:
                return io.takari.bpm.model.form.FormField.Cardinality.ONE_AND_ONLY_ONE;
            case ONE_OR_NONE:
                return io.takari.bpm.model.form.FormField.Cardinality.ONE_OR_NONE;
            default:
                throw new IllegalArgumentException("Unsupported cardinality type: " + c);
        }
    }

    private void writeData(Path baseDir, Object data) throws IOException {
        Path dst = baseDir.resolve("data.js");

        Path parent = dst.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        String s = String.format(DATA_FILE_TEMPLATE, objectMapper.writeValueAsString(data));
        Files.write(dst, s.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void copySharedResources(ProcessKey processKey, Path dst) throws IOException {
        Path sharedDir = dst.resolve(SHARED_DIR_NAME);
        if (!Files.exists(sharedDir)) {
            Files.createDirectories(sharedDir);
        }

        String resource = FormServiceV1.FORMS_RESOURCES_PATH + "/" + SHARED_DIR_NAME;
        stateManager.exportDirectory(processKey, resource, copyTo(sharedDir));
    }

    private static Response redirectToForm(UriInfo uriInfo, HttpHeaders headers,
                                           PartialProcessKey processKey, String formName) {

        String scheme = uriInfo.getBaseUri().getScheme();

        // check if we need to force https
        String origin = headers.getHeaderString("Origin");
        if (origin != null) {
            URI originUri = URI.create(origin);
            String originScheme = originUri.getScheme();
            if ("https".equalsIgnoreCase(originScheme)) {
                scheme = originScheme;
            }
        }

        UriBuilder b = UriBuilder.fromUri(uriInfo.getBaseUri())
                .scheme(scheme)
                .path(formPath(processKey, formName));

        return redirectTo(b.build().toString());
    }

    private static Response redirectTo(String path) {
        return Response.status(Status.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, path)
                .build();
    }

    private static String formPath(PartialProcessKey processKey, String formName) {
        return String.format(FORMS_PATH_TEMPLATE, processKey, formName);
    }
}
