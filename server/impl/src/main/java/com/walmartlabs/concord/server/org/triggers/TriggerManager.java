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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;
import java.util.UUID;

@Named
public class TriggerManager extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(TriggerManager.class);

    private final Map<String, TriggerProcessor> triggerProcessors;
    private final TriggersDao triggersDao;

    @Inject
    public TriggerManager(@MainDB Configuration cfg,
                          Map<String, TriggerProcessor> triggerProcessors,
                          TriggersDao triggersDao) {

        super(cfg);
        this.triggerProcessors = triggerProcessors;
        this.triggersDao = triggersDao;
    }

    public void refresh(UUID projectId, UUID repoId, ProjectDefinition pd) {
        tx(tx -> {
            triggersDao.delete(tx, projectId, repoId);

            pd.getTriggers().forEach(t -> {
                UUID triggerId = triggersDao.insert(tx,
                        projectId, repoId, t.getName(),
                        t.getActiveProfiles(), t.getArguments(), t.getParams(), t.getCfg());

                TriggerProcessor processor = triggerProcessors.get(t.getName());
                if (processor != null) {
                    processor.process(tx, triggerId, t);
                }
            });
        });

        log.info("refresh ['{}', '{}'] -> done, triggers count: {}", projectId, repoId, pd.getTriggers().size());
    }
}
