package com.walmartlabs.concord.server.events;

import com.walmartlabs.concord.server.api.events.OneopsResource;
import com.walmartlabs.concord.server.process.PayloadManager;
import com.walmartlabs.concord.server.process.pipelines.ProjectPipeline;
import com.walmartlabs.concord.server.process.pipelines.processors.Pipeline;
import com.walmartlabs.concord.server.triggers.TriggersDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named
public class OneopsResourceImpl extends AbstractEventResource implements OneopsResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(OneopsResourceImpl.class);

    private static final String EVENT_NAME = "oneops";

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

    @Inject
    public OneopsResourceImpl(PayloadManager payloadManager,
                              ProjectPipeline projectPipeline,
                              TriggersDao triggersDao) {
        super(EVENT_NAME, payloadManager, projectPipeline, triggersDao);
    }

    @Override
    public void event(Map<String, Object> event) {
        if (event == null || event.isEmpty()) {
            return;
        }

        Map<String, String> triggerConditions = buildConditions(event);
        Map<String, Object> triggerEvent = buildTriggerEvent(event, triggerConditions);

        String eventId = String.valueOf(event.get("cmsId"));
        process(eventId, triggerConditions, triggerEvent);

        log.info("event ['{}', '{}', '{}'] -> done", eventId, triggerConditions, triggerEvent);
    }

    private static Map<String, Object> buildTriggerEvent(Map<String, Object> event,
                                                         Map<String, String> conditions) {
        Map<String, Object> result = new HashMap<>();
        result.putAll(conditions);
        result.put("payload", event);
        return result;
    }


    @SuppressWarnings("unchecked")
    private static Map<String, String> buildConditions(Map<String, Object> event) {
        String[] nsPath = getNsPath(event);
        Map<String, Object> cis = getCis(event);
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");

        Map<String, String> result = new HashMap<>();
        result.put(STATE_KEY, get("ciState", cis));
        result.put(COMPONENT, get("ciClassName", cis));
        result.put(TYPE_KEY, get("type", event));
        result.put(SOURCE, get("source", event));
        result.put(SUBJECT, get("subject", event));
        result.put(DEPLOYMENT_STATE, get("deploymentState", payload));
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
        Object cisElement = event.get("cis");
        if (cisElement != null && cisElement instanceof List) {
            List<Map<String, Object>> cisItems = (List<Map<String, Object>>) cisElement;
            if (!cisItems.isEmpty()) {
                return cisItems.get(0);
            }
        }

        return Collections.emptyMap();
    }

    private static void addKey(String key, String[] values, int valueIndex, Map<String, String> result) {
        if (values.length > valueIndex) {
            result.put(key, values[valueIndex]);
        }
    }
}
