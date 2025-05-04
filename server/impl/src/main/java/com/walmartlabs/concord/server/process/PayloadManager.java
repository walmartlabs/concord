package com.walmartlabs.concord.server.process;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.copyTo;
import static com.walmartlabs.concord.server.process.state.ProcessStateManager.exclude;

public class PayloadManager {

    private static final String FORMS_PATH_PATTERN = String.format("%s/%s/%s/.*", Constants.Files.JOB_ATTACHMENTS_DIR_NAME,
            Constants.Files.JOB_STATE_DIR_NAME, Constants.Files.JOB_FORMS_DIR_NAME);

    private final ProcessStateManager stateManager;
    private final OrganizationDao orgDao;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;

    @Inject
    public PayloadManager(ProcessStateManager stateManager,
                          OrganizationDao orgDao,
                          ProjectDao projectDao,
                          RepositoryDao repositoryDao) {

        this.stateManager = stateManager;
        this.orgDao = orgDao;
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
    }

    @WithTimer
    public Payload createPayload(MultipartInput input, HttpServletRequest request) throws IOException {
        PartialProcessKey processKey = PartialProcessKey.create();

        UUID parentInstanceId = MultipartUtils.getUuid(input, Constants.Multipart.PARENT_INSTANCE_ID);

        UUID orgId = getOrg(input);
        UUID projectId = getProject(input, orgId);

        UUID repoId = getRepo(input, projectId);
        if (repoId != null && projectId == null) {
            // allow starting processes by specifying repository IDs without project IDs or names
            projectId = repositoryDao.getProjectId(repoId);
        }

        String entryPoint = MultipartUtils.getString(input, Constants.Multipart.ENTRY_POINT);

        UserPrincipal initiator = UserPrincipal.assertCurrent();

        String[] out = getOutExpressions(input);

        Map<String, Object> meta = MultipartUtils.getMap(input, Constants.Multipart.META);
        if (meta == null) {
            meta = Collections.emptyMap();
        }

        return PayloadBuilder.start(processKey)
                .parentInstanceId(parentInstanceId)
                .with(input)
                .organization(orgId)
                .project(projectId)
                .repository(repoId)
                .entryPoint(entryPoint)
                .outExpressions(out)
                .initiator(initiator.getId(), initiator.getUsername())
                .meta(meta)
                .request(request)
                .build();
    }

    /**
     * Creates a payload to resume a suspended process, pulling the necessary data from the state storage.
     */
    public Payload createResumePayload(ProcessKey processKey, String eventName, Map<String, Object> req) throws IOException {
        return createResumePayload(processKey, eventName != null ? Collections.singleton(eventName) : Collections.emptySet(), req);
    }

    /**
     * Creates a payload to resume a suspended process, pulling the necessary data from the state storage.
     */
    public Payload createResumePayload(ProcessKey processKey, Set<String> events, Map<String, Object> req) throws IOException {
        Path tmpDir = IOUtils.createTempDir("payload");
        if (!stateManager.export(processKey, copyTo(tmpDir))) {
            throw new ProcessException(processKey, "Can't resume '" + processKey + "', state snapshot not found");
        }

        return PayloadBuilder.resume(processKey)
                .workspace(tmpDir)
                .configuration(req)
                .resumeEvents(events)
                .build();
    }

    /**
     * Creates a payload to fork an existing process.
     */
    public Payload createFork(PartialProcessKey processKey, ProcessKey parentProcessKey, ProcessKind kind,
                              UUID initiatorId, String initiator, UUID projectId, Map<String, Object> req, String[] out,
                              Set<String> handlers, Imports imports) throws IOException {

        Path tmpDir = IOUtils.createTempDir("payload");

        // skip forms and the parent process' arguments
        if (!stateManager.export(parentProcessKey, exclude(copyTo(tmpDir), FORMS_PATH_PATTERN))) {
            throw new ProcessException(processKey, "Can't fork '" + parentProcessKey + "', the state snapshot not found");
        }

        return PayloadBuilder.start(processKey)
                .parentInstanceId(parentProcessKey.getInstanceId())
                .kind(kind)
                .initiator(initiatorId, initiator)
                .project(projectId)
                .configuration(req)
                .outExpressions(out)
                .workspace(tmpDir)
                .handlers(handlers)
                .imports(imports)
                .build();
    }

    private UUID getOrg(MultipartInput input) {
        UUID id = MultipartUtils.getUuid(input, Constants.Multipart.ORG_ID);
        String name = MultipartUtils.getString(input, Constants.Multipart.ORG_NAME);
        if (id == null && name != null) {
            id = orgDao.getId(name);
            if (id == null) {
                throw new ValidationErrorsException("Organization not found: " + name);
            }
        }
        return id;
    }

    private UUID getProject(MultipartInput input, UUID orgId) {
        UUID id = MultipartUtils.getUuid(input, Constants.Multipart.PROJECT_ID);
        String name = MultipartUtils.getString(input, Constants.Multipart.PROJECT_NAME);
        if (id == null && name != null) {
            if (orgId == null) {
                throw new ValidationErrorsException("Organization ID or name is required");
            }

            id = projectDao.getId(orgId, name);
            if (id == null) {
                throw new ValidationErrorsException("Project not found: " + name);
            }
        }
        return id;
    }

    private UUID getRepo(MultipartInput input, UUID projectId) {
        UUID id = MultipartUtils.getUuid(input, Constants.Multipart.REPO_ID);
        String name = MultipartUtils.getString(input, Constants.Multipart.REPO_NAME);
        if (id == null && name != null) {
            if (projectId == null) {
                throw new ValidationErrorsException("Project ID or name is required");
            }

            id = repositoryDao.getId(projectId, name);
            if (id == null) {
                throw new ValidationErrorsException("Repository not found: " + name);
            }
        }
        return id;
    }

    private String[] getOutExpressions(MultipartInput input) {
        String s = MultipartUtils.getString(input, Constants.Multipart.OUT_EXPR);
        if (s == null) {
            return null;
        }
        return s.split(",");
    }

    private static Function<PayloadBuilder, PayloadBuilder> p(EntryPoint p) {
        if (p == null) {
            return Function.identity();
        }

        return (b) -> b.organization(p.orgId)
                .project(p.projectId)
                .repository(p.repoId)
                .entryPoint(p.flow);
    }

    public static class EntryPoint implements Serializable {

        private static final long serialVersionUID = 1L;

        private final UUID orgId;
        private final UUID projectId;
        private final UUID repoId;
        private final String flow;

        public EntryPoint(UUID orgId, UUID projectId, UUID repoId, String flow) {
            this.orgId = orgId;
            this.projectId = projectId;
            this.repoId = repoId;
            this.flow = flow;
        }
    }
}
