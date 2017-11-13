package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.api.process.ProcessKind;
import com.walmartlabs.concord.server.process.PayloadParser.EntryPoint;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.project.RepositoryDao;
import com.walmartlabs.concord.server.team.TeamManager;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.copyTo;

@Named
public class PayloadManager {

    private static final String WORKSPACE_DIR_NAME = "workspace";
    private static final String INPUT_ARCHIVE_NAME = "_input.zip";

    private final ProcessStateManager stateManager;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;

    @Inject
    public PayloadManager(ProcessStateManager stateManager,
                          ProjectDao projectDao,
                          RepositoryDao repositoryDao) {

        this.stateManager = stateManager;
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
    }

    /**
     * Creates a payload. It is implied that all necessary resources to start a process are
     * supplied in the multipart data and/or provided by a project's repository or a template.
     *
     * @param instanceId
     * @param parentInstanceId
     * @param initiator
     * @param input
     * @return
     */
    public Payload createPayload(UUID instanceId, UUID parentInstanceId, String initiator,
                                 EntryPoint entryPoint, MultipartInput input, String[] out) throws IOException {

        Path baseDir = createPayloadDir();
        Path workspaceDir = ensureWorkspace(baseDir);

        Payload p = PayloadParser.parse(instanceId, parentInstanceId, baseDir, input)
                .putHeader(Payload.BASE_DIR, baseDir)
                .putHeader(Payload.WORKSPACE_DIR, workspaceDir);

        p = addInitiator(p, initiator);
        p = addOut(p, out);

        return addEntryPoint(p, entryPoint);
    }

    /**
     * Creates a payload from the supplied map of parameters.
     *
     * @param instanceId
     * @param parentInstanceId
     * @param initiator
     * @param request
     * @return
     */
    public Payload createPayload(UUID instanceId, UUID parentInstanceId, String initiator,
                                 EntryPoint entryPoint, Map<String, Object> request, String[] out) throws IOException {

        Path baseDir = createPayloadDir();
        Path workspaceDir = ensureWorkspace(baseDir);

        Payload p = new Payload(instanceId, parentInstanceId)
                .putHeader(Payload.BASE_DIR, baseDir)
                .putHeader(Payload.WORKSPACE_DIR, workspaceDir)
                .putHeader(Payload.REQUEST_DATA_MAP, request);

        p = addInitiator(p, initiator);
        p = addOut(p, out);

        return addEntryPoint(p, entryPoint);
    }

    /**
     * Creates a payload from an archive, containing all necessary resources.
     *
     * @param instanceId
     * @param parentInstanceId
     * @param initiator
     * @param in
     * @return
     */
    public Payload createPayload(UUID instanceId, UUID parentInstanceId, String initiator,
                                 EntryPoint entryPoint, InputStream in, String[] out) throws IOException {

        Path baseDir = createPayloadDir();
        Path workspaceDir = ensureWorkspace(baseDir);

        Path archive = baseDir.resolve(INPUT_ARCHIVE_NAME);
        Files.copy(in, archive);

        Payload p = new Payload(instanceId, parentInstanceId);

        p = addInitiator(p, initiator);
        p = addOut(p, out);

        p = p.putHeader(Payload.BASE_DIR, baseDir)
                .putHeader(Payload.WORKSPACE_DIR, workspaceDir)
                .putAttachment(Payload.WORKSPACE_ARCHIVE, archive);

        return addEntryPoint(p, entryPoint);
    }

    /**
     * Creates a payload from an archive, containing all necessary resources.
     *
     * @param instanceId
     * @param parentInstanceId
     * @param initiator
     * @param in
     * @return
     */
    public Payload createPayload(UUID instanceId, UUID parentInstanceId, String initiator,
                                 InputStream in, String[] out) throws IOException {

        Path baseDir = createPayloadDir();
        Path workspaceDir = ensureWorkspace(baseDir);

        Path archive = baseDir.resolve(INPUT_ARCHIVE_NAME);
        Files.copy(in, archive);

        Payload p = new Payload(instanceId, parentInstanceId);

        p = addInitiator(p, initiator);
        p = addOut(p, out);

        return p.putHeader(Payload.BASE_DIR, baseDir)
                .putHeader(Payload.WORKSPACE_DIR, workspaceDir)
                .putAttachment(Payload.WORKSPACE_ARCHIVE, archive);
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
        Path tmpDir = Files.createTempDirectory("payload");

        if (!stateManager.export(instanceId, copyTo(tmpDir))) {
            throw new ProcessException(instanceId, "Can't resume '" + instanceId + "', state snapshot not found");
        }

        return new Payload(instanceId)
                .putHeader(Payload.WORKSPACE_DIR, tmpDir)
                .putHeader(Payload.REQUEST_DATA_MAP, req)
                .putHeader(Payload.RESUME_EVENT_NAME, eventName);
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

        Path tmpDir = Files.createTempDirectory("payload");

        if (!stateManager.export(parentInstanceId, copyTo(tmpDir))) {
            throw new ProcessException(instanceId, "Can't fork '" + instanceId + "', parent state snapshot not found");
        }

        Payload p = new Payload(instanceId, parentInstanceId)
                .putHeader(Payload.PROCESS_KIND, kind)
                .putHeader(Payload.WORKSPACE_DIR, tmpDir)
                .putHeader(Payload.PROJECT_ID, projectId)
                .putHeader(Payload.REQUEST_DATA_MAP, req);

        p = addInitiator(p, initiator);
        p = addOut(p, out);

        return p;
    }

    private Path createPayloadDir() throws IOException {
        return Files.createTempDirectory("payload");
    }

    private Path ensureWorkspace(Path baseDir) throws IOException {
        Path p = baseDir.resolve(WORKSPACE_DIR_NAME);
        if (!Files.exists(p)) {
            Files.createDirectories(p);
        }
        return p;
    }

    private static Payload addInitiator(Payload p, String initiator) {
        if (initiator == null) {
            return p;
        }
        return p.putHeader(Payload.INITIATOR, initiator);
    }

    private static Payload addOut(Payload p, String[] out) {
        if (out == null || out.length == 0) {
            return p;
        }

        Set<String> s = new HashSet<>(Arrays.asList(out));
        return p.putHeader(Payload.OUT_EXPRESSIONS, s);
    }

    private Payload addEntryPoint(Payload p, EntryPoint e) {
        if (e == null) {
            return p;
        }

        // entry point specified in the request has the priority
        String entryPoint = e.getFlow();

        // if it wasn't specified in the request, we should check for
        // the existing entry point value
        if (entryPoint == null) {
            entryPoint = p.getHeader(Payload.ENTRY_POINT);
        }

        // we can also receive the entry point name in the request's
        // JSON data
        if (entryPoint == null) {
            Map<String, Object> req = p.getHeader(Payload.REQUEST_DATA_MAP);
            if (req != null) {
                entryPoint = (String) req.get(InternalConstants.Request.ENTRY_POINT_KEY);
            }
        }

        if (entryPoint != null) {
            p = p.putHeader(Payload.ENTRY_POINT, entryPoint)
                    .mergeValues(Payload.REQUEST_DATA_MAP, Collections.singletonMap(InternalConstants.Request.ENTRY_POINT_KEY, entryPoint));
        }

        UUID teamId = TeamManager.DEFAULT_TEAM_ID;

        UUID projectId = null;
        if (e.getProjectName() != null) {
            projectId = projectDao.getId(teamId, e.getProjectName());
            if (projectId == null) {
                throw new ProcessException(p.getInstanceId(), "Project not found: " + e.getProjectName());
            }
        }

        UUID repoId = null;
        if (projectId != null && e.getRepositoryName() != null) {
            repoId = repositoryDao.getId(projectId, e.getRepositoryName());
            if (repoId == null) {
                throw new ProcessException(p.getInstanceId(), "Repository not found: " + e.getRepositoryName());
            }
        }

        return p.putHeader(Payload.PROJECT_ID, projectId)
                .putHeader(Payload.REPOSITORY_ID, repoId);
    }
}
