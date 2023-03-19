package com.walmartlabs.concord.server.repository.listeners;

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

import com.walmartlabs.concord.process.loader.model.ProcessDefinition;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

public class ProcessDefinitionRefreshListener implements RepositoryRefreshListener {

    private static final Logger log = LoggerFactory.getLogger(ProcessDefinitionRefreshListener.class);

    private final RepositoryDao repositoryDao;

    @Inject
    public ProcessDefinitionRefreshListener(RepositoryDao repositoryDao) {
        this.repositoryDao = repositoryDao;
    }

    @Override
    public void onRefresh(DSLContext tx, RepositoryEntry repo, ProcessDefinition pd) {
        Set<String> pf = pd.publicFlows();
        if (pf == null || pf.isEmpty()) {
            // all flows are public when no public flows defined
            pf = new HashSet<>(pd.flows().keySet());
        }

        Set<String> entryPoints = pd.flows().keySet()
                .stream()
                .filter(pf::contains)
                .collect(Collectors.toSet());

        List<String> profiles = new ArrayList<>(pd.profiles().keySet());

        Map<String, Object> meta = new HashMap<>();
        meta.put("entryPoints", emptyToNull(entryPoints));
        meta.put("profiles", emptyToNull(profiles));
        repositoryDao.updateMeta(tx, repo.getId(), meta);

        log.info("onRefresh ['{}'] -> done ({})", repo.getId(), entryPoints);
    }

    private static <E extends Collection<?>> E emptyToNull(E items) {
        if (items.isEmpty()) {
            return null;
        }
        return items;
    }
}
