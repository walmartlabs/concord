package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.ForkDepthRule;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.jooq.Configuration;
import org.jooq.Record1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static org.jooq.impl.DSL.*;

public class ForkPolicyProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(ForkPolicyProcessor.class);

    private static final String DEFAULT_POLICY_MESSAGE = "Maximum number of forks exceeded: current {0}, limit {1}";

    private final ProcessLogManager logManager;
    private final ForkDepthDao forkDepthDao;

    @Inject
    public ForkPolicyProcessor(ProcessLogManager logManager, ForkDepthDao forkDepthDao) {
        this.logManager = logManager;
        this.forkDepthDao = forkDepthDao;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();
        UUID parentInstanceId = payload.getHeader(Payload.PARENT_INSTANCE_ID);

        PolicyEngine policy = payload.getHeader(Payload.POLICY);
        if (policy == null) {
            return chain.process(payload);
        }

        logManager.info(processKey, "Applying fork policies...");

        CheckResult<ForkDepthRule, Integer> result;
        try {
            result = policy.getForkDepthPolicy()
                    .check(() -> forkDepthDao.getDepth(parentInstanceId));
        } catch (Exception e) {
            log.error("process -> error", e);
            throw new ProcessException(processKey, "Found fork policy check error", e);
        }

        if (!result.getDeny().isEmpty()) {
            logManager.error(processKey, buildErrorMessage(result.getDeny()));
            throw new ProcessException(processKey, "Found fork policy violations");
        }
        return chain.process(payload);
    }

    private String buildErrorMessage(List<CheckResult.Item<ForkDepthRule, Integer>> errors) {
        StringBuilder sb = new StringBuilder();
        for (CheckResult.Item<ForkDepthRule, Integer> e : errors) {
            ForkDepthRule r = e.getRule();

            String msg = r.msg() != null ? r.msg() : DEFAULT_POLICY_MESSAGE;
            int actualCount = e.getEntity();
            int limit = r.max();

            sb.append(MessageFormat.format(Objects.requireNonNull(msg), actualCount, limit)).append(';');
        }
        return sb.toString();
    }

    private static class ForkDepthDao extends AbstractDao {

        @Inject
        public ForkDepthDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        @WithTimer
        public int getDepth(UUID parentInstanceId) {
            return txResult(tx -> tx.withRecursive("ancestors").as(
                    select(PROCESS_QUEUE.INSTANCE_ID, PROCESS_QUEUE.PARENT_INSTANCE_ID, field("1", Integer.class).as("depth"))
                            .from(PROCESS_QUEUE)
                            .where(PROCESS_QUEUE.INSTANCE_ID.eq(parentInstanceId))
                            .unionAll(
                                    select(PROCESS_QUEUE.INSTANCE_ID, PROCESS_QUEUE.PARENT_INSTANCE_ID, field("1 + ancestors.depth", Integer.class).as("depth"))
                                            .from(PROCESS_QUEUE)
                                            .join(name("ancestors"))
                                            .on(PROCESS_QUEUE.INSTANCE_ID.eq(
                                                    field(name("ancestors", "PARENT_INSTANCE_ID"), UUID.class)))))
                    .select(max(field(name("ancestors", "depth"), Integer.class)))
                    .from(name("ancestors"))
                    .fetchOne(Record1::value1));
        }
    }
}
