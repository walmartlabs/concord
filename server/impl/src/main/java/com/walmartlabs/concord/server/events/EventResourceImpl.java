package com.walmartlabs.concord.server.events;

import com.walmartlabs.concord.server.api.events.EventResource;
import com.walmartlabs.concord.server.process.PayloadManager;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.triggers.TriggersDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Named
public class EventResourceImpl extends AbstractEventResource implements EventResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(EventResourceImpl.class);

    @Inject
    public EventResourceImpl(PayloadManager payloadManager,
                             ProcessManager processManager,
                             TriggersDao triggersDao) {
        super(payloadManager, processManager, triggersDao);
    }

    @Override
    public Response event(String eventName, Map<String, Object> event) {
        if (event == null || event.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        String eventId = String.valueOf(Optional.ofNullable(event.get("id")).orElse(UUID.randomUUID()));
        int count = process(eventId, eventName, event, event);

        if (log.isDebugEnabled()) {
            log.debug("event ['{}', '{}', '{}'] -> done, {} processes started", eventId, eventName, event, count);
        } else {
            log.info("event ['{}', '{}'] -> done, {} processes started", eventId, eventName, count);
        }

        return Response.ok().build();
    }
}
