package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.process.PayloadParser.EntryPoint;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.copyTo;

@Named
public class PayloadManager {

    private static final String WORKSPACE_DIR_NAME = "workspace";
    private static final String INPUT_ARCHIVE_NAME = "_input.zip";

    private final ProcessStateManager stateManager;

    @Inject
    public PayloadManager(ProcessStateManager stateManager) {
        this.stateManager = stateManager;
    }

    /**
     * Creates a payload. It is implied that all necessary resources to start a process are
     * supplied in the multipart data and/or provided by a project's repository or a template.
     *
     * @param instanceId
     * @param initiator
     * @param input
     * @return
     */
    public Payload createPayload(String instanceId, String initiator, EntryPoint entryPoint, MultipartInput input) throws IOException {
        Path baseDir = createPayloadDir();
        Path workspaceDir = ensureWorkspace(baseDir);

        Payload p = PayloadParser.parse(instanceId, baseDir, input)
                .putHeader(Payload.BASE_DIR, baseDir)
                .putHeader(Payload.WORKSPACE_DIR, workspaceDir);

        p = addInitiator(p, initiator);
        return addEntryPoint(p, entryPoint);
    }

    /**
     * Creates a payload from the supplied map of parameters.
     *
     * @param instanceId
     * @param initiator
     * @param request
     * @return
     */
    public Payload createPayload(String instanceId, String initiator, EntryPoint entryPoint, Map<String, Object> request) throws IOException {
        Path baseDir = createPayloadDir();
        Path workspaceDir = ensureWorkspace(baseDir);

        Payload p = new Payload(instanceId)
                .putHeader(Payload.BASE_DIR, baseDir)
                .putHeader(Payload.WORKSPACE_DIR, workspaceDir)
                .putHeader(Payload.REQUEST_DATA_MAP, request);

        p = addInitiator(p, initiator);
        return addEntryPoint(p, entryPoint);
    }

    /**
     * Creates a payload from an archive, containing all necessary resources.
     *
     * @param instanceId
     * @param initiator
     * @param in
     * @return
     */
    public Payload createPayload(String instanceId, String initiator, InputStream in) throws IOException {
        Path baseDir = createPayloadDir();
        Path workspaceDir = ensureWorkspace(baseDir);

        Path archive = baseDir.resolve(INPUT_ARCHIVE_NAME);
        Files.copy(in, archive);

        Payload p = new Payload(instanceId);

        p = addInitiator(p, initiator);

        return p.putHeader(Payload.BASE_DIR, baseDir)
                .putHeader(Payload.WORKSPACE_DIR, workspaceDir)
                .putAttachment(Payload.WORKSPACE_ARCHIVE, archive);
    }

    /**
     * Creates a payload from an archive, containing all necessary resources and the
     * specified project name.
     *
     * @param instanceId
     * @param initiator
     * @param in
     * @return
     */
    public Payload createPayload(String instanceId, String initiator, String projectName, InputStream in) throws IOException {
        Payload p = createPayload(instanceId, initiator, in);
        p = addInitiator(p, initiator);
        return p.putHeader(Payload.PROJECT_NAME, projectName);
    }

    /**
     * Creates a payload to resume a suspended process, pulling the necessary data from the state storage.
     *
     * @param instanceId
     * @param eventName
     * @param req
     * @return
     */
    public Payload createResumePayload(String instanceId, String eventName, Map<String, Object> req) throws IOException {
        Path tmpDir = Files.createTempDirectory("payload");

        if (!stateManager.export(instanceId, copyTo(tmpDir))) {
            throw new ProcessException(instanceId, "Can't resume '" + instanceId + "', state snapshot not found");
        }

        return new Payload(instanceId)
                .putHeader(Payload.WORKSPACE_DIR, tmpDir)
                .putHeader(Payload.REQUEST_DATA_MAP, req)
                .putHeader(Payload.RESUME_EVENT_NAME, eventName);
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

    private static Payload addEntryPoint(Payload p, EntryPoint e) {
        if (e == null) {
            return p;
        }

        String[] rest = e.getEntryPoint();
        String realEntryPoint = rest.length > 0 ? rest[rest.length - 1] : null;
        return p.putHeader(Payload.PROJECT_NAME, e.getProjectName())
                .putHeader(Payload.ENTRY_POINT, e.getEntryPoint())
                .mergeValues(Payload.REQUEST_DATA_MAP, Collections.singletonMap(Constants.Request.ENTRY_POINT_KEY, realEntryPoint));
    }
}
