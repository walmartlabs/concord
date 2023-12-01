package com.walmartlabs.concord.server.org.triggers;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.process.loader.model.ProcessDefinition;
import com.walmartlabs.concord.process.loader.model.Trigger;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.policy.EntityAction;
import com.walmartlabs.concord.server.policy.EntityType;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.policy.PolicyUtils;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class TriggerManager extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(TriggerManager.class);

    private final ProjectDao projectDao;
    private final TriggersDao triggersDao;
    private final PolicyManager policyManager;
    private final TriggersConfiguration triggersCfg;

    private final CronTriggerProcessor cronTriggerProcessor;
    private final GithubTriggerEnricher githubTriggerEnricher;

    @Inject
    public TriggerManager(@MainDB Configuration cfg,
                          ProjectDao projectDao,
                          TriggersDao triggersDao,
                          PolicyManager policyManager,
                          TriggersConfiguration triggersCfg,
                          CronTriggerProcessor cronTriggerProcessor,
                          GithubTriggerEnricher githubTriggerEnricher) {

        super(cfg);

        this.projectDao = projectDao;
        this.triggersDao = triggersDao;
        this.policyManager = policyManager;
        this.triggersCfg = triggersCfg;

        this.cronTriggerProcessor = cronTriggerProcessor;
        this.githubTriggerEnricher = githubTriggerEnricher;
    }

    public void refresh(UUID projectId, UUID repoId, ProcessDefinition pd) {
        tx(tx -> refresh(projectId, repoId, pd));
    }

    public void refresh(DSLContext tx, UUID projectId, UUID repoId, ProcessDefinition pd) {
        UUID orgId = projectDao.getOrgId(projectId);
        for (Trigger t : pd.triggers()) {
            policyManager.checkEntity(orgId, projectId, EntityType.TRIGGER, EntityAction.CREATE, null, PolicyUtils.triggerToMap(orgId, projectId, t));
        }

        List<TriggerEntry> currentTriggers = triggersDao.list(tx, projectId, repoId);
        ListMultimap<String, TriggerEntry> triggerIds = toTriggerIds(currentTriggers);

        pd.triggers().forEach(t -> {
            t = enrichTriggerDefinition(tx, repoId, t);

            String internalId = TriggerInternalIdCalculator.getId(t.name(), t.activeProfiles(), t.arguments(), t.conditions(), t.configuration());
            List<TriggerEntry> triggers = triggerIds.get(internalId);
            if (!triggers.isEmpty()) {
                triggers.remove(0);
                return;
            }

            UUID triggerId = triggersDao.insert(tx,
                    projectId,
                    repoId,
                    t.name(),
                    t.activeProfiles(),
                    t.arguments(),
                    t.conditions(),
                    t.configuration());

            postProcessTrigger(tx, triggerId, t);
        });

        if (!triggerIds.isEmpty()) {
            triggersDao.delete(tx, triggerIds.values().stream().map(TriggerEntry::getId).collect(Collectors.toList()));
        }

        log.info("refresh ['{}', '{}'] -> done, triggers count: {}", projectId, repoId, pd.triggers().size());
    }

    public void clearTriggers(DSLContext tx, UUID projectId, UUID repoId) {
        triggersDao.delete(tx, projectId, repoId);
    }

    private Trigger enrichTriggerDefinition(DSLContext tx, UUID repoId, Trigger t) {
        Map<String, Object> conditions = merge(triggersCfg.getDefaultConditions(), t.name(), t.conditions());
        Map<String, Object> cfg = merge(triggersCfg.getDefaultConfiguration(), t.name(), t.configuration());

        // when we add more trigger types requiring a similar post-processing mechanism
        // then we should consider creating appropriate interfaces/listeners
        if ("github".equals(t.name())) {
            conditions = githubTriggerEnricher.enrich(tx, repoId, conditions);
        }

        return Trigger.builder().from(t)
                .conditions(conditions)
                .configuration(cfg)
                .build();
    }

    private void postProcessTrigger(DSLContext tx, UUID triggerId, Trigger t) {
        if ("cron".equals(t.name())) {
            cronTriggerProcessor.process(tx, triggerId, t);
        }
    }

    private static ListMultimap<String, TriggerEntry> toTriggerIds(List<TriggerEntry> triggers) {
        ListMultimap<String, TriggerEntry> result = ArrayListMultimap.create();
        for (TriggerEntry t : triggers) {
            String internalId = TriggerInternalIdCalculator.getId(t.getEventSource(), t.getActiveProfiles(), t.getArguments(), t.getConditions(), t.getCfg());
            result.put(internalId, t);
        }
        return result;
    }

    private static Map<String, Object> merge(Map<String, Object> cfg, String key, Map<String, Object> original) {
        if (cfg == null) {
            return original;
        }

        Map<String, Object> m = MapUtils.getMap(cfg, key, null);
        if (m == null) {
            m = MapUtils.getMap(cfg, "_", Collections.emptyMap());
        }

        return ConfigurationUtils.deepMerge(m, original);
    }
}
