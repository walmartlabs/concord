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
import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.server.ExtraStatus;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.policy.PolicyEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import org.jooq.Configuration;
import org.jooq.Record4;
import org.jooq.SelectConditionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.*;

import static com.walmartlabs.concord.policyengine.QueueProcessPolicy.ProcessRule;
import static com.walmartlabs.concord.policyengine.QueueProcessPolicy.QueueMetrics;
import static com.walmartlabs.concord.server.jooq.Tables.V_PROCESS_QUEUE;
import static org.jooq.impl.DSL.*;

@Named
public class QueuePolicyProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(QueuePolicyProcessor.class);

    private static final String DEFAULT_POLICY_MESSAGE = "Maximum number of {0} processes exceeded: current {1}, limit {2}";

    private final QueueMetricsDao dao;

    @Inject
    public QueuePolicyProcessor(QueueMetricsDao dao) {
        this.dao = dao;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();

        PolicyEntry policy = payload.getHeader(Payload.POLICY);
        if (policy == null || policy.isEmpty()) {
            return chain.process(payload);
        }

        UUID orgId = payload.getHeader(Payload.ORGANIZATION_ID);
        UUID prjId = payload.getHeader(Payload.PROJECT_ID);

        CheckResult<ProcessRule, Integer> result;
        try {
            result = new PolicyEngine(policy.getRules())
                    .getQueueProcessPolicy()
                    .check(statuses -> dao.metrics(orgId, prjId, statuses));
        } catch (Exception e) {
            log.error("process ['{}'] -> error", instanceId, e);
            throw new ProcessException(instanceId, "Error while processing queue policies", e);
        }

        if (!result.getDeny().isEmpty()) {
            throw new ProcessException(instanceId, "Process queue policy violations: " + buildErrorMessage(result.getDeny()), ExtraStatus.TOO_MANY_REQUESTS);
        }

        return chain.process(payload);
    }

    private static String buildErrorMessage(List<CheckResult.Item<ProcessRule, Integer>> errors) {
        StringBuilder sb = new StringBuilder();
        for(CheckResult.Item<ProcessRule, Integer> e : errors) {
            ProcessRule r = e.getRule();

            String msg = r.getMsg() != null ? r.getMsg() : DEFAULT_POLICY_MESSAGE;
            String status = r.getStatus();
            int actualCount = e.getEntity();
            int limit = r.getMax();

            sb.append(MessageFormat.format(msg, status, actualCount, limit)).append(';');
        }
        return sb.toString();
    }

    @Named
    private static class QueueMetricsDao extends AbstractDao {

        @Inject
        public QueueMetricsDao(@Named("app") Configuration cfg) {
            super(cfg);
        }

        @WithTimer
        public QueueMetrics metrics(UUID orgId, UUID prjId, Set<String> statuses) {
            return txResult(tx -> {

                SelectConditionStep<Record4<Integer, Integer, Integer, String>> q = tx.select(
                        field("1", Integer.class).as("count_process"),
                        when(V_PROCESS_QUEUE.ORG_ID.eq(orgId), 1).otherwise(0).as("count_per_org"),
                        when(V_PROCESS_QUEUE.PROJECT_ID.eq(prjId), 1).otherwise(0).as("count_per_project"),
                        V_PROCESS_QUEUE.CURRENT_STATUS.as("status"))
                        .from(V_PROCESS_QUEUE)
                        .where(V_PROCESS_QUEUE.CURRENT_STATUS.in(statuses));

                List<Record4<BigDecimal, BigDecimal, BigDecimal, String>> result = tx.select(
                        sum(q.field("count_process", Integer.class)),
                        sum(q.field("count_per_org", Integer.class)),
                        sum(q.field("count_per_project", Integer.class)),
                        q.field("status", String.class))
                        .from(q)
                        .groupBy(q.field("status", String.class))
                        .fetch();

                Map<String, Integer> process = new HashMap<>();
                Map<String, Integer> perOrg = new HashMap<>();
                Map<String, Integer> perProject = new HashMap<>();

                result.forEach(r -> {
                    String status = r.value4();
                    process.put(status, getInt(r.value1()));
                    perOrg.put(status, getInt(r.value2()));
                    perProject.put(status, getInt(r.value3()));
                });

                return new QueueMetrics(process, perOrg, perProject);
            });
        }

        private static int getInt(BigDecimal p) {
            if (p == null) {
                return 0;
            }
            return p.intValue();
        }
    }
}
