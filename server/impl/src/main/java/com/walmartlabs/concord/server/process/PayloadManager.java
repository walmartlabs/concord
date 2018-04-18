package com.walmartlabs.concord.server.process;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.api.process.ProcessKind;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.copyTo;

@Named
public class PayloadManager {

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

    public Payload createPayload(MultipartInput input) throws IOException {
        UUID instanceId = UUID.randomUUID();
        UUID parentInstanceId = MultipartUtils.getUuid(input, "parentInstanceId");

        UUID orgId = getOrg(input);
        UUID projectId = getProject(input, orgId);

        UUID repoId = getRepo(input, projectId);
        if (repoId != null && projectId == null) {
            // allow starting processes using a repository ID only
            projectId = repositoryDao.getProjectId(repoId);
        }

        String entryPoint = MultipartUtils.getString(input, "entryPoint");

        UserPrincipal initiator = UserPrincipal.assertCurrent();

        String[] out = getOutExpressions(input);

        return new PayloadBuilder(instanceId)
                .parentInstanceId(parentInstanceId)
                .with(input)
                .organization(orgId)
                .project(projectId)
                .repository(repoId)
                .entryPoint(entryPoint)
                .outExpressions(out)
                .initiator(initiator.getUsername())
                .build();
    }

    /**
     * Creates a payload. It is implied that all necessary resources to start a process are
     * supplied in the multipart data and/or provided by a project's repository or a template.
     *
     * @param instanceId
     * @param parentInstanceId
     * @param initiator
     * @param input
     * @deprecated prefer {@link #createPayload(MultipartInput)}
     * @return
     */
    @Deprecated
    public Payload createPayload(UUID instanceId, UUID parentInstanceId, String initiator,
                                 EntryPoint entryPoint, MultipartInput input, String[] out) throws IOException {

        return new PayloadBuilder(instanceId)
                .parentInstanceId(parentInstanceId)
                .with(input)
                .apply(p(entryPoint))
                .initiator(initiator)
                .outExpressions(out)
                .build();
    }

    /**
     * Creates a payload from the supplied map of parameters.
     *
     * @param instanceId
     * @param parentInstanceId
     * @param initiator
     * @param request
     * @deprecated prefer {@link #createPayload(MultipartInput)}
     * @return
     */
    @Deprecated
    public Payload createPayload(UUID instanceId, UUID parentInstanceId, String initiator,
                                 EntryPoint entryPoint, Map<String, Object> request, String[] out) throws IOException {

        return new PayloadBuilder(instanceId)
                .parentInstanceId(parentInstanceId)
                .initiator(initiator)
                .apply(p(entryPoint))
                .configuration(request)
                .outExpressions(out)
                .build();
    }

    /**
     * Creates a payload from an archive, containing all necessary resources.
     *
     * @param instanceId
     * @param parentInstanceId
     * @param initiator
     * @param in
     * @deprecated prefer {@link #createPayload(MultipartInput)}
     * @return
     */
    @Deprecated
    public Payload createPayload(UUID instanceId, UUID parentInstanceId, String initiator,
                                 EntryPoint entryPoint, InputStream in, String[] out) throws IOException {

        return new PayloadBuilder(instanceId)
                .parentInstanceId(parentInstanceId)
                .initiator(initiator)
                .apply(p(entryPoint))
                .workspace(in)
                .outExpressions(out)
                .build();
    }

    /**
     * Creates a payload from an archive, containing all necessary resources.
     *
     * @param instanceId
     * @param parentInstanceId
     * @param initiator
     * @param in
     * @deprecated prefer {@link #createPayload(MultipartInput)}
     * @return
     */
    @Deprecated
    public Payload createPayload(UUID instanceId, UUID parentInstanceId, String initiator,
                                 InputStream in, String[] out) throws IOException {

        return new PayloadBuilder(instanceId)
                .parentInstanceId(parentInstanceId)
                .initiator(initiator)
                .workspace(in)
                .outExpressions(out)
                .build();
    }

    /**
     * Creates a payload to resume a suspended process, pulling the necessary data from the state storage.
     *
     * @param instanceId
     * @param eventName
     * @param req
     * @return
     */
    public Payload createResumePayload(UUID instanceId, String eventName, Map<String, Object> req) throws IOException {
        Path tmpDir = IOUtils.createTempDir("payload");

        if (!stateManager.export(instanceId, copyTo(tmpDir))) {
            throw new ProcessException(instanceId, "Can't resume '" + instanceId + "', state snapshot not found");
        }

        return new PayloadBuilder(instanceId)
                .workspace(tmpDir)
                .configuration(req)
                .resumeEventName(eventName)
                .build();
    }

    /**
     * Creates a payload to fork an existing process.
     *
     * @param instanceId
     * @param parentInstanceId
     * @param projectId
     * @param req
     * @return
     */
    public Payload createFork(UUID instanceId, UUID parentInstanceId, ProcessKind kind,
                              String initiator, UUID projectId, Map<String, Object> req, String[] out) throws IOException {

        Path tmpDir = IOUtils.createTempDir("payload");
        if (!stateManager.export(parentInstanceId, copyTo(tmpDir))) {
            throw new ProcessException(instanceId, "Can't fork '" + instanceId + "', parent state snapshot not found");
        }

        return new PayloadBuilder(instanceId)
                .parentInstanceId(parentInstanceId)
                .kind(kind)
                .initiator(initiator)
                .project(projectId)
                .configuration(req)
                .outExpressions(out)
                .workspace(tmpDir)
                .build();
    }

    public void assertAcceptsRawPayload(Payload payload) {
        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        if (projectId == null) {
            return;
        }

        Optional<Boolean> o = projectDao.isAcceptsRawPayload(projectId);
        if (!o.isPresent()) {
            throw new ProcessException(payload.getInstanceId(), "Project not found: " + projectId);
        }

        if (!o.get()) {
            throw new ProcessException(payload.getInstanceId(), "The project is not accepting raw payloads: " + projectId,
                    Status.BAD_REQUEST);
        }
    }

    public EntryPoint parseEntryPoint(UUID instanceId, UUID orgId, String entryPoint) {
        if (entryPoint == null) {
            return null;
        }

        String[] as = entryPoint.split(":");
        if (as.length < 1 || as.length > 3) {
            throw new ValidationErrorsException("Invalid entry point format: " + entryPoint);
        }

        String projectName = as[0].trim();
        UUID projectId = projectDao.getId(orgId, projectName);
        if (projectId == null) {
            throw new ProcessException(instanceId, "Project not found: " + projectName);
        }

        String repoName = null;
        if (as.length > 1) {
            repoName = as[1].trim();

        }

        String flow = null;
        if (as.length > 2) {
            flow = as[2].trim();
        }

        return createEntryPoint(instanceId, orgId, projectName, repoName, flow);
    }

    public EntryPoint createEntryPoint(UUID instanceId, UUID orgId, String projectName, String repoName, String flow) {
        UUID projectId = projectDao.getId(orgId, projectName);
        if (projectId == null) {
            throw new ProcessException(instanceId, "Project not found: " + projectName);
        }

        UUID repoId = null;
        if (repoName != null) {
            repoId = repositoryDao.getId(projectId, repoName);
            if (repoId == null) {
                throw new ProcessException(instanceId, "Repository not found: " + repoName);
            }
        }

        return new PayloadManager.EntryPoint(orgId, projectId, repoId, flow);
    }

    private UUID getOrg(MultipartInput input) {
        UUID id = MultipartUtils.getUuid(input, "orgId");
        String name = MultipartUtils.getString(input, "org");
        if (id == null && name != null) {
            id = orgDao.getId(name);
            if (id == null) {
                throw new ValidationErrorsException("Organization not found: " + name);
            }
        }
        return id;
    }

    private UUID getProject(MultipartInput input, UUID orgId) {
        UUID id = MultipartUtils.getUuid(input, "projectId");
        String name = MultipartUtils.getString(input, "project");
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
        UUID id = MultipartUtils.getUuid(input, "repoId");
        String name = MultipartUtils.getString(input, "repo");
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
        String s = MultipartUtils.getString(input, "out");
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
