package com.walmartlabs.concord.server.process.queue;

import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.DSLContext;

public interface ProcessStatusListener {

    void onStatusChange(DSLContext tx, ProcessKey processKey, ProcessStatus status);
}
