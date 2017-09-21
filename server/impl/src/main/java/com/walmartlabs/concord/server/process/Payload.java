package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.server.process.keys.AttachmentKey;
import com.walmartlabs.concord.server.process.keys.HeaderKey;

import java.nio.file.Path;
import java.util.*;

public class Payload {

    public static final HeaderKey<String> PROJECT_NAME = HeaderKey.register("_projectName", String.class);
    public static final HeaderKey<String> REPOSITORY_NAME = HeaderKey.register("_repoName", String.class);
    public static final HeaderKey<String> ENTRY_POINT = HeaderKey.register("_entryPoint", String.class);
    public static final HeaderKey<String> INITIATOR = HeaderKey.register("_initiator", String.class);
    public static final HeaderKey<Path> WORKSPACE_DIR = HeaderKey.register("_workspace", Path.class);
    public static final HeaderKey<Path> BASE_DIR = HeaderKey.register("_baseDir", Path.class);
    public static final HeaderKey<Map> REQUEST_DATA_MAP = HeaderKey.register("_meta", Map.class);
    public static final HeaderKey<String> RESUME_EVENT_NAME = HeaderKey.register("_resumeEventName", String.class);
    public static final HeaderKey<ProjectDefinition> PROJECT_DEFINITION = HeaderKey.register("_projectDef", ProjectDefinition.class);
    public static final HeaderKey<Collection<String>> ACTIVE_PROFILES = HeaderKey.registerCollection("_activeProfiles");

    public static final AttachmentKey WORKSPACE_ARCHIVE = AttachmentKey.register("archive");

    private final UUID instanceId;
    private final UUID parentInstanceId;
    private final Map<String, Object> headers;
    private final Map<String, Path> attachments;

    public Payload(UUID instanceId) {
        this(instanceId, null);
    }

    public Payload(UUID instanceId, UUID parentInstanceId) {
        this.instanceId = instanceId;
        this.parentInstanceId = parentInstanceId;
        this.headers = Collections.emptyMap();
        this.attachments = Collections.emptyMap();
    }

    private Payload(Payload old, Map<String, Object> headers, Map<String, Path> attachments) {
        this.instanceId = old.instanceId;
        this.parentInstanceId = old.parentInstanceId;
        this.headers = Objects.requireNonNull(headers, "Headers map cannot be null");
        this.attachments = Objects.requireNonNull(attachments, "Attachments map cannot be null");
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public UUID getParentInstanceId() {
        return parentInstanceId;
    }

    public <T> T getHeader(HeaderKey<T> key) {
        return key.cast(headers.get(key.name()));
    }

    public <T> T getHeader(HeaderKey<T> key, T defaultValue) {
        Object v = headers.get(key.name());
        if (v == null) {
            return defaultValue;
        }
        return key.cast(v);
    }

    @SuppressWarnings("unchecked")
    public <T> T getHeader(String key) {
        return (T) headers.get(key);
    }

    public Map<String, Object> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public <T> Payload putHeader(HeaderKey<T> key, T value) {
        Map<String, Object> m = new HashMap<>(headers);
        m.put(key.name(), key.cast(value));
        return new Payload(this, m, this.attachments);
    }

    public Payload putHeaders(Map<String, Object> values) {
        Map<String, Object> m = new HashMap<>(headers);
        m.putAll(values);
        return new Payload(this, m, this.attachments);
    }

    public Payload removeHeader(HeaderKey<?> key) {
        Map<String, Object> m = new HashMap<>(headers);
        m.remove(key.name());
        return new Payload(this, m, this.attachments);
    }

    @SuppressWarnings("unchecked")
    public Payload mergeValues(HeaderKey<Map> key, Map values) {
        Map o = getHeader(key);
        Map n = new HashMap(o != null ? o : Collections.emptyMap());
        n.putAll(values);
        return putHeader(key, n);
    }

    public Path getAttachment(AttachmentKey key) {
        return key.cast(attachments.get(key.name()));
    }

    public Map<String, Path> getAttachments() {
        return Collections.unmodifiableMap(attachments);
    }

    public Payload putAttachment(AttachmentKey key, Path value) {
        Map<String, Path> m = new HashMap<>(attachments);
        m.put(key.name(), value);
        return new Payload(this, this.headers, m);
    }

    public Payload putAttachments(Map<String, Path> values) {
        Map<String, Path> m = new HashMap<>(attachments);
        m.putAll(values);
        return new Payload(this, this.headers, m);
    }

    public Payload removeAttachment(AttachmentKey key) {
        if (!attachments.containsKey(key.name())) {
            return this;
        }

        return removeAttachment(key.name());
    }

    public Payload removeAttachment(String key) {
        Map<String, Path> m = new HashMap<>(attachments);
        m.remove(key);
        return new Payload(this, this.headers, m);
    }

    public Payload clearAttachments() {
        return new Payload(this, this.headers, Collections.emptyMap());
    }

    @Override
    public String toString() {
        return "Payload{" +
                "instanceId='" + instanceId + '\'' +
                ", headers=" + headers.size() +
                ", attachments=" + attachments.size() +
                '}';
    }
}
