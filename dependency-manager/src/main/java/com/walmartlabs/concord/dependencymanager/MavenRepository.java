package com.walmartlabs.concord.dependencymanager;

import java.io.Serializable;

public class MavenRepository implements Serializable {

    private final String id;
    private final String contentType;
    private final String url;
    private boolean snapshotEnabled;

    public MavenRepository(String id, String contentType, String url, boolean snapshotEnabled) {
        this.id = id;
        this.contentType = contentType;
        this.url = url;
        this.snapshotEnabled = snapshotEnabled;
    }

    public String getId() {
        return id;
    }

    public String getContentType() {
        return contentType;
    }

    public String getUrl() {
        return url;
    }

    public boolean isSnapshotEnabled() {
        return snapshotEnabled;
    }

    @Override
    public String toString() {
        return "MavenRepository{" +
                "id='" + id + '\'' +
                ", contentType='" + contentType + '\'' +
                ", url='" + url + '\'' +
                ", snapshotEnabled=" + snapshotEnabled +
                '}';
    }
}
