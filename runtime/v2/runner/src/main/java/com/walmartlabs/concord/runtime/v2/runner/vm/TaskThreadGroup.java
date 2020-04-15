package com.walmartlabs.concord.runtime.v2.runner.vm;

public class TaskThreadGroup extends ThreadGroup {

    private final String segmentId;

    public TaskThreadGroup(String name, String segmentId) {
        super(name);
        this.segmentId = segmentId;
    }

    public String getSegmentId() {
        return segmentId;
    }
}
