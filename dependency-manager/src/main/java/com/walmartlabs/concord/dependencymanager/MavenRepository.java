package com.walmartlabs.concord.dependencymanager;

import java.io.Serializable;

public class MavenRepository implements Serializable {

    private final String id;
    private final String contentType;
    private final String url;

    public MavenRepository(String id, String contentType, String url) {
        this.id = id;
        this.contentType = contentType;
        this.url = url;
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

    @Override
    public String toString() {
        return "MavenRepository{" +
                "id='" + id + '\'' +
                ", contentType='" + contentType + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
