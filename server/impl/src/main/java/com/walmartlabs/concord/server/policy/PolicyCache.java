package com.walmartlabs.concord.server.policy;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.PolicyEngineRules;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.cfg.PolicyCacheConfiguration;
import com.walmartlabs.concord.server.sdk.BackgroundTask;
import org.immutables.value.Value;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.Tables.POLICIES;
import static com.walmartlabs.concord.server.jooq.Tables.POLICY_LINKS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class PolicyCache implements BackgroundTask {

    private static final Logger log = LoggerFactory.getLogger(PolicyCache.class);
    private static final long ERROR_DELAY = 10000;

    private final ObjectMapper objectMapper;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock refreshMutex = new ReentrantLock();

    private final PolicyCacheConfiguration cacheCfg;
    private final Dao dao;

    private PolicyEngine defaultPolicy;
    private Map<UUID, PolicyEngine> byOrg = Collections.emptyMap();
    private Map<UUID, PolicyEngine> byProject = Collections.emptyMap();
    private Map<UUID, PolicyEngine> byUser = Collections.emptyMap();
    private List<PolicyItem> otherUserPolicies = Collections.emptyList();

    private volatile long lastRefreshRequestAt = -1;
    private Thread loader;

    @Inject
    public PolicyCache(ObjectMapper objectMapper, PolicyCacheConfiguration cacheCfg, Dao dao) {
        this.objectMapper = objectMapper.copy()
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);
        this.cacheCfg = cacheCfg;
        this.dao = dao;
    }

    @Override
    public void start() {
        this.loader = new Thread(this::run, "policy-cache-loader");
        this.loader.start();
    }

    @Override
    public void stop() {
        if (loader != null) {
            loader.interrupt();
            loader = null;
        }
    }

    public void refresh() {
        try {
            reloadPolicies();
        } catch (Exception e) {
            refreshMutex.lock();
            try {
                lastRefreshRequestAt = System.currentTimeMillis();
                refreshMutex.notifyAll();
            } finally {
                refreshMutex.unlock();
            }
        }
    }

    public PolicyEngine get(UUID orgId, UUID projectId, UUID userId) {
        Lock l = rwLock.readLock();
        l.lock();
        try {
            return getUnsafe(orgId, projectId, userId);
        } finally {
            l.unlock();
        }
    }

    private PolicyEngine getUnsafe(UUID orgId, UUID projectId, UUID userId) {
        if (userId != null) {
            if (projectId != null) {
                PolicyEngine result = otherUserPolicies.stream()
                        .filter(p -> userId.equals(p.link().userId()))
                        .filter(p -> projectId.equals(p.link().projectId()))
                        .findAny()
                        .map(PolicyItem::engine)
                        .orElse(null);

                if (result != null) {
                    return result;
                }
            }

            if (orgId != null) {
                PolicyEngine result = otherUserPolicies.stream()
                        .filter(p -> userId.equals(p.link().userId()))
                        .filter(p -> orgId.equals(p.link().orgId()))
                        .findAny()
                        .map(PolicyItem::engine)
                        .orElse(null);

                if (result != null) {
                    return result;
                }
            }

            PolicyEngine result = byUser.get(userId);
            if (result != null) {
                return result;
            }
        }

        if (projectId != null) {
            PolicyEngine result = byProject.get(projectId);
            if (result != null) {
                return result;
            }
        }

        if (orgId != null) {
            PolicyEngine result = byOrg.get(orgId);
            if (result != null) {
                return result;
            }
        }

        return defaultPolicy;
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                long now = System.currentTimeMillis();
                reloadPolicies();

                refreshMutex.lock();
                try {
                    if (lastRefreshRequestAt > now) {
                        lastRefreshRequestAt = now;
                    } else {
                        //noinspection ResultOfMethodCallIgnored
                        refreshMutex.newCondition()
                                .await(cacheCfg.getReloadInterval().toMillis(), MILLISECONDS);
                    }
                } finally {
                    refreshMutex.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("run -> error", e);

                try {
                    Thread.sleep(ERROR_DELAY);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void reloadPolicies() {
        PolicyEngine defaultPolicy = null;
        Map<UUID, PolicyEngine> byOrg = new HashMap<>();
        Map<UUID, PolicyEngine> byProject = new HashMap<>();
        Map<UUID, PolicyEngine> byUser = new HashMap<>();
        List<PolicyItem> otherUserPolicies = new ArrayList<>();

        List<PolicyLink> links = dao.listLinks();
        if (links.isEmpty()) {
            setPolicies(defaultPolicy, byOrg, byProject, byUser, otherUserPolicies);
            return;
        }

        Map<UUID, Policy> policies = mergePolicies(dao.listPolicies());
        for (PolicyLink l : links) {
            Policy policy = policies.get(l.policyId());
            if (policy == null) {
                continue;
            }
            PolicyEngine pe = new PolicyEngine(policy.policyNames(), policy.rules());
            if (l.orgId() == null && l.projectId() == null && l.userId() == null) {
                defaultPolicy = pe;
            } else if (l.orgId() != null && l.projectId() == null && l.userId() == null) {
                byOrg.put(l.orgId(), pe);
            } else if (l.orgId() == null && l.projectId() != null && l.userId() == null) {
                byProject.put(l.projectId(), pe);
            } else if (l.orgId() == null && l.projectId() == null && l.userId() != null) {
                byUser.put(l.userId(), pe);
            } else if (l.userId() != null) {
                otherUserPolicies.add(PolicyItem.of(l, pe));
            } else {
                log.warn("Unexpected policy link: {}", l);
            }
        }

        setPolicies(defaultPolicy, byOrg, byProject, byUser, otherUserPolicies);
    }

    private void setPolicies(PolicyEngine defaultPolicy,
                             Map<UUID, PolicyEngine> byOrg,
                             Map<UUID, PolicyEngine> byProject,
                             Map<UUID, PolicyEngine> byUser,
                             List<PolicyItem> otherUserPolicies) {

        Lock l = rwLock.writeLock();
        l.lock();
        try {
            this.defaultPolicy = defaultPolicy;
            this.byOrg = byOrg;
            this.byProject = byProject;
            this.byUser = byUser;
            this.otherUserPolicies = otherUserPolicies;
        } finally {
            l.unlock();
        }
    }

    Map<UUID, Policy> mergePolicies(List<PolicyRules> policies) {
        Map<UUID, Policy> result = new HashMap<>();
        for (PolicyRules p : policies) {
            List<PolicyRules> rules = combinePolicies(p, policies);
            Map<String, Object> mergedRules = mergeRules(rules);

            result.put(p.id(), ImmutablePolicy.builder()
                    .id(p.id())
                    .addAllPolicyNames(rules.stream().map(PolicyRules::name).collect(Collectors.toList()))
                    .rules(objectMapper.convertValue(mergedRules, PolicyEngineRules.class))
                    .build());
        }
        return result;
    }

    private static Map<String, Object> mergeRules(List<PolicyRules> rules) {
        Map<String, Object> result = new HashMap<>();
        for (int i = rules.size() - 1; i >= 0; i--) {
            result = ConfigurationUtils.deepMerge(result, rules.get(i).rules());
        }
        return result;
    }

    private static List<PolicyRules> combinePolicies(PolicyRules p, List<PolicyRules> policies) {
        List<PolicyRules> result = new ArrayList<>();
        result.add(p);

        PolicyRules current = p;
        while (current != null) {
            UUID parentId = current.parentId();
            if (parentId == null) {
                return result;
            }
            PolicyRules parent = policies.stream()
                    .filter(r -> r.id().equals(parentId))
                    .findAny().orElse(null);
            if (parent != null) {
                result.add(parent);
            }
            current = parent;
        }
        return result;
    }

    static class Dao extends AbstractDao {

        private final ConcordObjectMapper objectMapper;

        @Inject
        public Dao(@MainDB Configuration cfg,
                   ConcordObjectMapper objectMapper) {
            super(cfg);

            this.objectMapper = objectMapper;
        }

        public List<PolicyLink> listLinks() {
            return txResult(tx -> tx.selectFrom(POLICY_LINKS)
                    .fetch(r -> ImmutablePolicyLink.builder()
                            .policyId(r.getPolicyId())
                            .orgId(r.getOrgId())
                            .projectId(r.getProjectId())
                            .userId(r.getUserId())
                            .build()));
        }

        public List<PolicyRules> listPolicies() {
            return txResult(tx -> tx.selectFrom(POLICIES)
                    .fetch(r -> ImmutablePolicyRules.builder()
                            .id(r.getPolicyId())
                            .parentId(r.getParentPolicyId())
                            .name(r.getPolicyName())
                            .rules(objectMapper.fromJSONB(r.getRules()))
                            .build()));
        }
    }

    @Value.Immutable
    interface PolicyLink {

        UUID policyId();

        @Nullable
        UUID orgId();

        @Nullable
        UUID projectId();

        @Nullable
        UUID userId();
    }

    @Value.Immutable
    interface PolicyRules {

        UUID id();

        @Nullable
        UUID parentId();

        String name();

        Map<String, Object> rules();

        static ImmutablePolicyRules.Builder builder() {
            return ImmutablePolicyRules.builder();
        }
    }

    @Value.Immutable
    interface PolicyItem {

        @Value.Parameter
        PolicyLink link();

        @Value.Parameter
        PolicyEngine engine();

        static PolicyItem of(PolicyLink link, PolicyEngine engine) {
            return ImmutablePolicyItem.of(link, engine);
        }
    }

    @Value.Immutable
    interface Policy {

        UUID id();

        List<String> policyNames();

        PolicyEngineRules rules();
    }
}
