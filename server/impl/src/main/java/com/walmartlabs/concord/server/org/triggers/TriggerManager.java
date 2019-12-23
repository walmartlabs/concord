package com.walmartlabs.concord.server.org.triggers;

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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.process.loader.model.ProjectDefinition;
import com.walmartlabs.concord.server.process.loader.model.Trigger;
import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.policy.EntityAction;
import com.walmartlabs.concord.server.policy.EntityType;
import com.walmartlabs.concord.server.policy.PolicyManager;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.policy.PolicyUtils.toMap;

@Named
public class TriggerManager extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(TriggerManager.class);

    private final Map<String, TriggerProcessor> triggerProcessors;
    private final ProjectDao projectDao;
    private final TriggersDao triggersDao;
    private final PolicyManager policyManager;
    private final TriggersConfiguration triggersCfg;

    @Inject
    public TriggerManager(@MainDB Configuration cfg,
                          Map<String, TriggerProcessor> triggerProcessors,
                          ProjectDao projectDao,
                          TriggersDao triggersDao,
                          PolicyManager policyManager,
                          TriggersConfiguration triggersCfg) {

        super(cfg);
        this.triggerProcessors = triggerProcessors;
        this.projectDao = projectDao;
        this.triggersDao = triggersDao;
        this.policyManager = policyManager;
        this.triggersCfg = triggersCfg;
    }

    public void refresh(UUID projectId, UUID repoId, ProjectDefinition pd) {
        UUID orgId = projectDao.getOrgId(projectId);
        for(Trigger t : pd.triggers()) {
            policyManager.checkEntity(orgId, projectId, EntityType.TRIGGER, EntityAction.CREATE, null, toMap(orgId, projectId, t));
        }

        tx(tx -> {
            triggersDao.delete(tx, projectId, repoId);

            pd.triggers().forEach(t -> {
                Map<String, Object> conditions = merge(triggersCfg.getDefaultConditions(), t.name(), t.conditions());
                Map<String, Object> cfg = merge(triggersCfg.getDefaultConfiguration(), t.name(), t.configuration());

                UUID triggerId = triggersDao.insert(tx,
                        projectId,
                        repoId,
                        t.name(),
                        t.activeProfiles(),
                        t.arguments(),
                        conditions,
                        cfg);

                TriggerProcessor processor = triggerProcessors.get(t.name());
                if (processor != null) {
                    processor.process(tx, repoId, triggerId, t);
                }
            });
        });

        log.info("refresh ['{}', '{}'] -> done, triggers count: {}", projectId, repoId, pd.triggers().size());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> merge(Map<String, Object> cfg, String key, Map<String, Object> original) {
        if (cfg == null) {
            return original;
        }

        Map<String, Object> defaults = (Map<String, Object>) cfg.getOrDefault(key, Collections.emptyMap());
        return ConfigurationUtils.deepMerge(defaults, original);
    }
}
