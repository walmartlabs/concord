package com.walmartlabs.concord.plugins.boo.oneops;

import java.io.Serializable;

public class Deployment implements Serializable {
    long id;
    String nsPath;
    String status;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getNsPath() {
        return nsPath;
    }

    public void setNsPath(String nsPath) {
        this.nsPath = nsPath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Deployment{" +
                "id=" + id +
                ", nsPath='" + nsPath + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
