package com.walmartlabs.concord.server.console;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.validation.ConcordId;
import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.cfg.CustomFormConfiguration;
import com.walmartlabs.concord.server.process.ConcordFormService;
import com.walmartlabs.concord.server.process.ConcordFormService.FormSubmitResult;
import com.walmartlabs.concord.server.process.FormUtils;
import com.walmartlabs.concord.server.process.FormUtils.ValidationException;
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.ProcessStatus;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import io.takari.bpm.form.DefaultFormValidatorLocale;
import io.takari.bpm.form.Form;
import io.takari.bpm.form.FormSubmitResult.ValidationError;
import io.takari.bpm.form.FormValidatorLocale;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.bpm.model.form.FormField;
import io.takari.bpm.model.form.FormField.Cardinality;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.copyTo;

@Named
@Singleton
@javax.ws.rs.Path("/api/service/custom_form")
public class CustomFormService implements Resource {

    private static final Logger log = LoggerFactory.getLogger(CustomFormService.class);

    public static final String FORMS_PATH_PREFIX = "/forms/";

    private static final String FORM_DIR_NAME = "form";
    private static final String SHARED_DIR_NAME = "shared";
    private static final String FORMS_PATH_TEMPLATE = FORMS_PATH_PREFIX + "%s/%s/" + FORM_DIR_NAME + "/";
    private static final String DATA_FILE_TEMPLATE = "data = %s;";

    private static final String NON_BRANDED_FORM_URL_TEMPLATE = "/#/process/%s/form/%s?fullScreen=true&wizard=true";
    private static final String FORM_WIZARD_CONTINUE_URL_TEMPLATE = "/api/service/custom_form/%s/%s/continue";

    private static final long STATUS_REFRESH_DELAY = 250;

    private final CustomFormConfiguration cfg;
    private final ConcordFormService formService;
    private final ProcessStateManager stateManager;
    private final ProcessQueueDao queueDao;
    private final ObjectMapper objectMapper;
    private final FormValidatorLocale validatorLocale;

    @Inject
    public CustomFormService(CustomFormConfiguration cfg,
                             ConcordFormService formService,
                             ProcessStateManager stateManager,
                             ProcessQueueDao queueDao) {

        this.cfg = cfg;
        this.formService = formService;
        this.stateManager = stateManager;
        this.queueDao = queueDao;

        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        this.validatorLocale = new DefaultFormValidatorLocale();
    }

    @POST
    @javax.ws.rs.Path("{processInstanceId}/{formInstanceId}/start")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public FormSessionResponse startSession(@PathParam("processInstanceId") UUID processInstanceId,
                                            @PathParam("formInstanceId") @ConcordId String formInstanceId) {

        // TODO locking
        Form form = assertForm(processInstanceId, formInstanceId);

        Path dst = cfg.getBaseDir()
                .resolve(processInstanceId.toString())
                .resolve(formInstanceId);

        try {
            Path formDir = dst.resolve(FORM_DIR_NAME);
            if (!Files.exists(formDir)) {
                Files.createDirectories(formDir);
            }

            String resource = ConcordFormService.FORMS_RESOURCES_PATH + "/" + form.getFormDefinition().getName();
            // copy original branding files into the target directory
            boolean branded = stateManager.exportDirectory(processInstanceId, resource, copyTo(formDir));
            if (!branded) {
                // not branded, redirect to the default wizard
                String uri = String.format(NON_BRANDED_FORM_URL_TEMPLATE, processInstanceId, formInstanceId);
                return new FormSessionResponse(uri);
            }

            // create JS file containing the form's data
            writeData(formDir, initialData(form));

            // copy shared resources (if present)
            copySharedResources(processInstanceId, formInstanceId, dst);
        } catch (IOException e) {
            log.warn("startSession ['{}', '{}'] -> error while preparing a custom form: {}",
                    processInstanceId, formInstanceId, e);
            throw new ConcordApplicationException("Error while preparing a custom form", e);
        }

        return new FormSessionResponse(formPath(processInstanceId, formInstanceId));
    }

    @POST
    @javax.ws.rs.Path("{processInstanceId}/{formInstanceId}/continue")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public Response continueSession(@Context UriInfo uriInfo,
                                    @Context HttpHeaders headers,
                                    @PathParam("processInstanceId") UUID processInstanceId,
                                    @PathParam("formInstanceId") @ConcordId String formInstanceId,
                                    MultivaluedMap<String, String> data) {

        return continueSession(uriInfo, headers, processInstanceId, formInstanceId, FormUtils.convert(data));
    }

    @POST
    @javax.ws.rs.Path("{processInstanceId}/{formInstanceId}/continue")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response continueSession(@Context UriInfo uriInfo,
                                    @Context HttpHeaders headers,
                                    @PathParam("processInstanceId") UUID processInstanceId,
                                    @PathParam("formInstanceId") @ConcordId String formInstanceId,
                                    MultipartInput data) {

        return continueSession(uriInfo, headers, processInstanceId, formInstanceId, MultipartUtils.toMap(data));
    }

    private Response continueSession(UriInfo uriInfo, HttpHeaders headers,
                                     UUID processInstanceId, String formInstanceId,
                                     Map<String, Object> data) {
        // TODO locking
        Form form = assertForm(processInstanceId, formInstanceId);

        // TODO constants
        Map<String, Object> opts = form.getOptions();
        boolean yield = opts != null && (boolean) opts.getOrDefault("yield", false);

        Path dst = cfg.getBaseDir()
                .resolve(processInstanceId.toString())
                .resolve(formInstanceId);

        Path formDir = dst.resolve(FORM_DIR_NAME);

        try {
            Map<String, Object> m = new HashMap<>();
            try {
                m = FormUtils.convert(validatorLocale, form, data);

                FormSubmitResult r = formService.submit(processInstanceId, formInstanceId, m);
                if (r.isValid()) {
                    if (yield) {
                        // this was the last "interactive" form. The process will continue in "background"
                        // and users should get a success page.
                        writeData(formDir, success(form, m));
                    } else {
                        while (true) {
                            ProcessEntry entry = queueDao.get(processInstanceId);
                            ProcessStatus s = entry.getStatus();

                            if (s == ProcessStatus.SUSPENDED) {
                                String nextFormId = formService.nextFormId(processInstanceId);
                                if (nextFormId == null) {
                                    writeData(formDir, success(form, m));
                                    break;
                                } else {
                                    FormSessionResponse nextSession = startSession(processInstanceId, nextFormId);
                                    return redirectTo(nextSession.getUri());
                                }
                            } else if (s == ProcessStatus.FAILED || s == ProcessStatus.CANCELLED) {
                                writeData(formDir, processFailed(form, m));
                                break;
                            } else if (s == ProcessStatus.FINISHED) {
                                writeData(formDir, success(form, m));
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
                    writeData(formDir, prepareData(form, m, r.getErrors()));
                }
            } catch (ValidationException e) {
                ValidationError err = new ValidationError(e.getField().getName(), e.getMessage());
                FormData d = prepareData(form, m, Collections.singletonList(err));
                writeData(formDir, d);
            }
        } catch (Exception e) {
            throw new ConcordApplicationException("Error while submitting a form", e);
        }

        return redirectToForm(uriInfo, headers, processInstanceId, formInstanceId);
    }

    private Form assertForm(UUID processInstanceId, String formInstanceId) {
        Form form = formService.get(processInstanceId, formInstanceId);
        if (form == null) {
            log.warn("assertForm ['{}', '{}'] -> not found", processInstanceId, formInstanceId);
            throw new ConcordApplicationException("Form not found", Status.NOT_FOUND);
        }
        return form;
    }

    private FormData initialData(Form form) {
        return prepareData(false, false, form, null, false, null);
    }

    private FormData success(Form form, Map<String, Object> overrides) {
        return prepareData(true, false, form, overrides, false, null);
    }

    private FormData processFailed(Form form, Map<String, Object> overrides) {
        return prepareData(false, true, form, overrides, false, null);
    }

    private FormData prepareData(Form form, Map<String, Object> overrides, List<ValidationError> errors) {
        return prepareData(false, false, form, overrides, true, errors);
    }


    @SuppressWarnings("unchecked")
    private FormData prepareData(boolean success, boolean processFailed, Form form,
                                 Map<String, Object> overrides, boolean skipMissingOverrides,
                                 List<ValidationError> errors) {

        String processInstanceId = form.getProcessBusinessKey();
        String formInstanceId = form.getFormInstanceId().toString();

        String submitUrl = String.format(FORM_WIZARD_CONTINUE_URL_TEMPLATE, processInstanceId, formInstanceId);

        // TODO merge with FormResource
        Map<String, FormDataDefinition> _definitions = new HashMap<>();

        // the order of precedence should be:
        //   submitted value > form call value > field's default value > environment value
        Map<String, Object> _values = new HashMap<>();
        Map<String, String> _errors = new HashMap<>();

        FormDefinition fd = form.getFormDefinition();

        Map<String, Object> env = form.getEnv();
        Map<String, Object> data = env != null ? (Map<String, Object>) env.get(fd.getName()) : Collections.emptyMap();
        if (data == null) {
            data = new HashMap<>();
        }

        Map<String, Object> extra = null;
        Map<String, Object> opts = form.getOptions();
        if (opts != null) {
            extra = (Map<String, Object>) opts.get("values");
        }

        if (extra != null) {
            data = ConfigurationUtils.deepMerge(data, extra);
            _values.putAll(extra);
        }

        for (FormField f : fd.getFields()) {
            _definitions.put(f.getName(), new FormDataDefinition(f.getLabel(), f.getType(), f.getCardinality(), f.getAllowedValue()));

            Object v = overrides != null ? overrides.get(f.getName()) : null;
            if (v == null && skipMissingOverrides) {
                continue;
            }

            if (v == null) {
                v = data.get(f.getName());
            }

            if (v == null) {
                continue;
            }

            _values.put(f.getName(), v);
        }

        if (errors != null) {
            for (ValidationError e : errors) {
                _errors.put(e.getFieldName(), e.getError());
            }
        }

        return new FormData(success, processFailed, submitUrl, _definitions, _values, _errors);
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

    private void copySharedResources(UUID processInstanceId, String formInstanceId, Path dst) throws IOException {
        Path sharedDir = dst.resolve(SHARED_DIR_NAME);
        if (!Files.exists(sharedDir)) {
            Files.createDirectories(sharedDir);
        }

        String resource = ConcordFormService.FORMS_RESOURCES_PATH + "/" + SHARED_DIR_NAME;
        stateManager.exportDirectory(processInstanceId, resource, copyTo(sharedDir));
    }

    private static Response redirectToForm(UriInfo uriInfo, HttpHeaders headers,
                                           UUID processInstanceId, String formInstanceId) {

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
                .path(formPath(processInstanceId, formInstanceId));

        return redirectTo(b.build().toString());
    }

    private static Response redirectTo(String path) {
        return Response.status(Status.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, path)
                .build();
    }

    private static String formPath(UUID processInstanceId, String formInstanceId) {
        return String.format(FORMS_PATH_TEMPLATE, processInstanceId, formInstanceId);
    }

    @JsonInclude(Include.NON_EMPTY)
    private static class FormData implements Serializable {

        private final boolean success;
        private final boolean processFailed;
        private final String submitUrl;
        private final Map<String, FormDataDefinition> definitions;
        private final Map<String, Object> values;
        private final Map<String, String> errors;

        public FormData(boolean success, boolean processFailed, String submitUrl,
                        Map<String, FormDataDefinition> definitions, Map<String, Object> values,
                        Map<String, String> errors) {

            this.success = success;
            this.processFailed = processFailed;
            this.submitUrl = submitUrl;
            this.definitions = definitions;
            this.values = values;
            this.errors = errors;
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isProcessFailed() {
            return processFailed;
        }

        public String getSubmitUrl() {
            return submitUrl;
        }

        public Map<String, FormDataDefinition> getDefinitions() {
            return definitions;
        }

        public Map<String, Object> getValues() {
            return values;
        }

        public Map<String, String> getErrors() {
            return errors;
        }
    }

    @JsonInclude(Include.NON_NULL)
    private static class FormDataDefinition implements Serializable {

        private final String label;
        private final String type;
        private final Cardinality cardinality;
        private final Object allow;

        private FormDataDefinition(String label, String type, Cardinality cardinality, Object allow) {
            this.label = label;
            this.type = type;
            this.cardinality = cardinality;
            this.allow = allow;
        }

        public String getLabel() {
            return label;
        }

        public String getType() {
            return type;
        }

        public Cardinality getCardinality() {
            return cardinality;
        }

        public Object getAllow() {
            return allow;
        }
    }
}
