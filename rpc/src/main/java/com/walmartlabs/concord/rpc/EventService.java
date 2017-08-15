package com.walmartlabs.concord.rpc;

import java.io.Serializable;
import java.util.Date;

public interface EventService {

    void onEvent(String instanceId, Date date, EventType type, Serializable data) throws ClientException;
}
