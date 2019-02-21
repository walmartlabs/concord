package com.walmartlabs.concord.server.events;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.cfg.ExternalEventsConfiguration;
import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.user.UserManager;
import io.swagger.annotations.Api;
import io.swagger.annotations.Authorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Named
@Singleton
@Api(value = "Events", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/events")
public class OneOpsEventResource extends AbstractEventResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(OneOpsEventResource.class);

    private static final String EVENT_SOURCE = "oneops";

    private static final String ORG_KEY = "org";
    private static final String ASM_KEY = "asm";
    private static final String ENV_KEY = "env";
    private static final String PLATFORM_KEY = "platform";
    private static final String STATE_KEY = "state";
    private static final String TYPE_KEY = "type";
    private static final String COMPONENT_KEY = "component";
    private static final String SOURCE_KEY = "source";
    private static final String SUBJECT_KEY = "subject";
    private static final String DEPLOYMENT_STATE_KEY = "deploymentState";
    private static final String IPS_KEY = "ips";
    private static final String AUTHOR_KEY = "author";

    private final ObjectMapper objectMapper;

    @Inject
    public OneOpsEventResource(ExternalEventsConfiguration cfg,
                               ProcessManager processManager,
                               TriggersDao triggersDao,
                               ProjectDao projectDao,
                               RepositoryDao repositoryDao,
                               TriggersConfiguration triggersCfg,
                               UserManager userManager,
                               LdapManager ldapManager) {

        super(cfg, processManager, triggersDao, projectDao, repositoryDao, triggersCfg, userManager, ldapManager);
        this.objectMapper = new ObjectMapper();
    }

    @POST
    @Path("/oneops")
    @Consumes(MediaType.APPLICATION_JSON)
    @WithTimer
    public Response event(Map<String, Object> event) {
        if (event == null || event.isEmpty()) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        Map<String, Object> triggerConditions = buildConditions(event);
        Map<String, Object> triggerEvent = buildTriggerEvent(event, triggerConditions);

        String eventId = String.valueOf(event.get("cmsId"));
        int count = process(eventId, EVENT_SOURCE, triggerConditions, triggerEvent, null);

        log.info("event ['{}', '{}', '{}'] -> done, {} processes started", eventId, triggerConditions, triggerEvent, count);
        return Response.ok().build();
    }

    @POST
    @Path("/oneops")
    @Consumes(MediaType.WILDCARD)
    @WithTimer
    @SuppressWarnings("unchecked")
    public Response event(InputStream in) {
        Map<String, Object> m;
        try {
            m = objectMapper.readValue(in, Map.class);
        } catch (IOException e) {
            throw new ConcordApplicationException("Error while reading JSON data: " + e.getMessage(), e);
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
        Map<String, Object> cis = getCis(event);
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");

        Map<String, Object> result = new HashMap<>();
        result.put(STATE_KEY, get("ciState", cis));
        result.put(COMPONENT_KEY, get("ciClassName", cis));
        result.put(TYPE_KEY, get("type", event));
        result.put(SOURCE_KEY, get("source", event));
        result.put(SUBJECT_KEY, get("subject", event));
        result.put(DEPLOYMENT_STATE_KEY, get("deploymentState", payload));

        result.put(IPS_KEY, getIPs(event));
        result.put(AUTHOR_KEY, payload.get("createdBy"));

        // example: /testing/twst/localtest/bom/sts/1
        //          / org   /asm /env      /.../platform/...
        String[] nsPath = getNsPath(event);
        if (nsPath != null) {
            addKey(ORG_KEY, nsPath, 0, result);
            addKey(ASM_KEY, nsPath, 1, result);
            addKey(ENV_KEY, nsPath, 2, result);
            // ignore bom
            addKey(PLATFORM_KEY, nsPath, 4, result);
        }

        return result;
    }

    private static String get(String key, Map<String, Object> event) {
        return String.valueOf(event.get(key));
    }

    private static String[] getNsPath(Map<String, Object> event) {
        Map<String, Object> cis = getCis(event);
        if (cis == null) {
            return null;
        }

        String nsPath = (String) cis.get("nsPath");
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
        if (cisItems.isEmpty()) {
            return Collections.emptyMap();
        }
        return cisItems.get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getCisItems(Map<String, Object> event) {
        Object cisElement = event.get("cis");
        if (cisElement == null) {
            return Collections.emptyList();
        }

        if (cisElement instanceof List) {
            return (List<Map<String, Object>>) cisElement;
        }

        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static Set<String> getIPs(Map<String, Object> event) {
        List<Map<String, Object>> cisItems = getCisItems(event);
        if (cisItems.isEmpty()) {
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
