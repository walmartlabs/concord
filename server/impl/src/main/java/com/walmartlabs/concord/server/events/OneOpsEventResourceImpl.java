package com.walmartlabs.concord.server.events;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.api.events.OneOpsEventResource;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.process.ProcessManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Named
public class OneOpsEventResourceImpl extends AbstractEventResource implements OneOpsEventResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(OneOpsEventResourceImpl.class);

    private static final String EVENT_SOURCE = "oneops";

    private static final String ORG_KEY = "org";
    private static final String ASM_KEY = "asm";
    private static final String ENV_KEY = "env";
    private static final String PLATFORM_KEY = "platform";
    private static final String STATE_KEY = "state";
    private static final String TYPE_KEY = "type";
    private static final String COMPONENT = "component";
    private static final String SOURCE = "source";
    private static final String SUBJECT = "subject";
    private static final String DEPLOYMENT_STATE = "deploymentState";
    private static final String IP_ADDRESSES = "ips";

    private final ObjectMapper objectMapper;

    @Inject
    public OneOpsEventResourceImpl(ProcessManager processManager,
                                   TriggersDao triggersDao,
                                   ProjectDao projectDao) {

        super(processManager, triggersDao, projectDao);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Response event(Map<String, Object> event) {
        if (event == null || event.isEmpty()) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        Map<String, Object> triggerConditions = buildConditions(event);
        Map<String, Object> triggerEvent = buildTriggerEvent(event, triggerConditions);

        String eventId = String.valueOf(event.get("cmsId"));
        int count = process(eventId, EVENT_SOURCE, triggerConditions, triggerEvent);

        if (log.isDebugEnabled()) {
            log.debug("event ['{}', '{}', '{}'] -> done, {} processes started", eventId, triggerConditions, triggerEvent, count);
        } else {
            log.info("event ['{}'] -> done, {} processes started", eventId, count);
        }

        return Response.ok().build();
    }

    @Override
    public Response event(InputStream in) {
        Map<String, Object> m;
        try {
            m = objectMapper.readValue(in, Map.class);
        } catch (IOException e) {
            throw new WebApplicationException("Error while reading JSON data: " + e.getMessage(), e);
        }

        return event(m);
    }

    private static Map<String, Object> buildTriggerEvent(Map<String, Object> event,
                                                         Map<String, Object> conditions) {

        Map<String, Object> result = new HashMap<>(conditions);
        result.put("payload", event);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildConditions(Map<String, Object> event) {
        String[] nsPath = getNsPath(event);

        Map<String, Object> cis = getCis(event);
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");

        Map<String, Object> result = new HashMap<>();
        result.put(STATE_KEY, get("ciState", cis));
        result.put(COMPONENT, get("ciClassName", cis));
        result.put(TYPE_KEY, get("type", event));
        result.put(SOURCE, get("source", event));
        result.put(SUBJECT, get("subject", event));
        result.put(DEPLOYMENT_STATE, get("deploymentState", payload));

        result.put(IP_ADDRESSES, getIPs(event));

        addKey(ORG_KEY, nsPath, 0, result);
        addKey(ASM_KEY, nsPath, 1, result);
        addKey(ENV_KEY, nsPath, 2, result);
        addKey(PLATFORM_KEY, nsPath, 3, result);

        return result;
    }

    private static String get(String key, Map<String, Object> event) {
        return String.valueOf(event.get(key));
    }

    private static String[] getNsPath(Map<String, Object> event) {
        String nsPath = (String) event.get("nsPath");
        if (nsPath == null) {
            return new String[0];
        }
        if (nsPath.startsWith("/")) {
            nsPath = nsPath.substring(1);
        }
        return nsPath.split("/");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getCis(Map<String, Object> event) {
        List<Map<String, Object>> cisItems = getCisItems(event);
        if (cisItems == null || cisItems.isEmpty()) {
            return Collections.emptyMap();
        }
        return cisItems.get(0);
    }

    private static List<Map<String, Object>> getCisItems(Map<String, Object> event) {
        Object cisElement = event.get("cis");
        if (cisElement == null) {
            return Collections.emptyList();
        }

        if (cisElement instanceof List) {
            List<Map<String, Object>> cisItems = (List<Map<String, Object>>) cisElement;
            return cisItems;
        }

        return Collections.emptyList();
    }

    private static Set<String> getIPs(Map<String, Object> event) {
        List<Map<String, Object>> cisItems = getCisItems(event);
        if (cisItems == null || cisItems.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>();
        for (Map<String, Object> i : cisItems) {
            Object attrs = i.get("ciAttributes");
            if (attrs == null) {
                continue;
            }

            Map<String, Object> m = (Map<String, Object>) attrs;
            Object v = m.get("public_ip");
            if (v == null) {
                continue;
            }

            result.add(v.toString());
        }
        return result;
    }

    private static void addKey(String key, String[] values, int valueIndex, Map<String, Object> result) {
        if (values.length > valueIndex) {
            result.put(key, values[valueIndex]);
        }
    }
}
