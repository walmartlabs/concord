package com.walmartlabs.concord.server.plugins.noderoster.processor;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.common.StringUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.plugins.noderoster.HostManager;
import com.walmartlabs.concord.server.plugins.noderoster.db.NodeRosterDB;
import com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterHostArtifacts;
import com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.records.NodeRosterHostArtifactsRecord;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.immutables.value.Value;
import org.jooq.BatchBindStep;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;

import static com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterHostArtifacts.NODE_ROSTER_HOST_ARTIFACTS;
import static org.jooq.impl.DSL.value;

/**
 * Collect "get_url", "maven_artifact" and other related events, extracts
 * the artifact data and saved in the DB.
 */
@Named
public class HostArtifactsProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(HostArtifactsProcessor.class);

    private final Dao dao;
    private final HostManager hosts;

    @Inject
    public HostArtifactsProcessor(Dao dao, HostManager hosts) {
        this.dao = dao;
        this.hosts = hosts;
    }

    @Override
    @WithTimer
    public void process(List<AnsibleEvent> events) {
        List<HostArtifactItem> items = new ArrayList<>();

        for (AnsibleEvent e : events) {
            String host = e.data().getHost();
            Set<String> urls = getArtifactUrl(e.data());
            if (host != null && urls != null) {
                for (String url : urls) {
                    items.add(HostArtifactItem.builder()
                            .instanceId(e.instanceId())
                            .instanceCreatedAt(e.instanceCreatedAt())
                            .host(hosts.getOrCreate(host))
                            .artifactUrl(url)
                            .build());
                }
            }
        }

        if (!items.isEmpty()) {
            dao.insert(items);
        }

        log.info("process -> events: {}, items: {}", events.size(), items.size());
    }

    private static Set<String> getArtifactUrl(EventData eventData) {
        if (!eventData.isPostEvent()) {
            return null;
        }

        if (!eventData.isOk()) {
            return null;
        }

        String action = eventData.getAction();
        if (action == null) {
            return null;
        }

        switch (action) {
            case "maven_artifact": {
                return fromMavenArtifact(eventData);
            }
            case "get_url":
                return fromGetUrl(eventData);
            case "uri": {
                return fromUri(eventData);
            }
        }

        return null;
    }

    // /$groupId[0]/../$groupId[n]/$artifactId/$version/$artifactId-$version-$classifier.$extension
    private static Set<String> fromMavenArtifact(EventData eventData) {
        return processResult(eventData, result -> {
            String repoUrl = (String) result.get("repository_url");
            String groupId = (String) result.get("group_id");
            String artifactId = (String) result.get("artifact_id");
            String extension = (String) result.get("extension");
            String classifier = (String) result.get("classifier");
            String version = (String) result.get("version");

            if (repoUrl == null || groupId == null || artifactId == null || version == null) {
                return null;
            }

            String name = artifactId + "-" + version;
            if (classifier != null && !classifier.isEmpty()) {
                name += "-" + classifier;
            }
            name += "." + extension;

            return normalizeUrl(repoUrl) + "/" + groupId.replaceAll("\\.", "/") + "/" + artifactId + "/" + version + "/" + name;
        });
    }

    private static Set<String> fromGetUrl(EventData eventData) {
        return processResult(eventData, r -> {
            Object url = r.get("url");
            if (url instanceof String) {
                return (String) url;
            }
            return null;
        });
    }


    private static Set<String> fromUri(EventData eventData) {
        return processResult(eventData, r -> {
            if (r.get("path") == null) {
                return null;
            }
            Object url = r.get("url");
            if (url instanceof String) {
                return (String) url;
            }
            return null;
        });
    }

    private static Set<String> processResult(EventData eventData, Function<Map<String, Object>, String> processor) {
        Map<String, Object> result = eventData.getMap("result");
        if (result == null) {
            return Collections.emptySet();
        }

        String singleResult = processor.apply(result);
        if (singleResult != null) {
            return Collections.singleton(singleResult);
        }

        return processResults(result, processor);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> processResults(Map<String, Object> result, Function<Map<String, Object>, String> processor) {
        Object resultsObject = result.get("results");
        if (!(resultsObject instanceof Collection)) {
            return Collections.emptySet();
        }

        Set<String> processed = new HashSet<>();
        Collection<Map<String, Object>> results = (Collection<Map<String, Object>>) resultsObject;
        for (Object ro : results) {
            if (ro instanceof Map) {
                Map<String, Object> r = (Map<String, Object>) ro;
                String value = processor.apply(r);
                if (value != null) {
                    processed.add(value);
                }
            }
        }
        return processed;
    }

    private static String normalizeUrl(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    @Named
    public static class Dao extends AbstractDao {

        private final HostArtifactPartitioner partitioner;

        @Inject
        public Dao(@NodeRosterDB Configuration cfg, HostArtifactPartitioner partitioner) {
            super(cfg);
            this.partitioner = partitioner;
        }

        @WithTimer
        public void insert(List<HostArtifactItem> items) {
            tx(tx -> insert(tx, items));
        }

        private void insert(DSLContext tx, List<HostArtifactItem> items) {
            NodeRosterHostArtifacts h = NODE_ROSTER_HOST_ARTIFACTS.as("ha");

            Map<Table<NodeRosterHostArtifactsRecord>, Collection<HostArtifactItem>> tblItems = partitioner.process(items);
            for (Map.Entry<Table<NodeRosterHostArtifactsRecord>, Collection<HostArtifactItem>> e : tblItems.entrySet()) {
                BatchBindStep q = tx.batch(tx.insertInto(e.getKey().as("ha"), h.INSTANCE_ID, h.INSTANCE_CREATED_AT, h.HOST_ID, h.ARTIFACT_URL)
                        .values((UUID) null, null, null, null)
                        .onConflictDoNothing());

                for (HostArtifactItem i : e.getValue()) {
                    q.bind(value(i.instanceId()), value(i.instanceCreatedAt()), value(i.host()), value(StringUtils.abbreviate(i.artifactUrl(), h.ARTIFACT_URL.getDataType().length())));
                }

                q.execute();
            }
        }
    }

    @Named
    public static class HostArtifactPartitioner extends Partitioner<HostArtifactItem, NodeRosterHostArtifactsRecord> {

        public HostArtifactPartitioner() {
            super(NODE_ROSTER_HOST_ARTIFACTS, HostArtifactItem::instanceCreatedAt);
        }
    }

    @Value.Immutable
    interface HostArtifactItem {

        UUID host();

        UUID instanceId();

        OffsetDateTime instanceCreatedAt();

        String artifactUrl();

        static ImmutableHostArtifactItem.Builder builder() {
            return ImmutableHostArtifactItem.builder();
        }
    }
}
