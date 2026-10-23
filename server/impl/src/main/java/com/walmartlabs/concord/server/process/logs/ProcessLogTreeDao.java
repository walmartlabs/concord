package com.walmartlabs.concord.server.process.logs;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import com.walmartlabs.concord.server.jooq.tables.ProcessLogSegments;
import com.walmartlabs.concord.server.jooq.tables.records.ProcessLogSegmentsRecord;
import com.walmartlabs.concord.server.process.LogSegment;
import com.walmartlabs.concord.server.process.LogSegmentTreeNode;
import com.walmartlabs.concord.server.process.LogSegmentTreePage;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.Configuration;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SortField;
import org.jooq.Table;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_LOG_SEGMENTS;
import static com.walmartlabs.concord.server.jooq.enums.LogSegmentKind.ATTEMPT;
import static com.walmartlabs.concord.server.jooq.enums.LogSegmentKind.ITERATION;
import static org.jooq.impl.DSL.*;

/** Indexed child and subtree reads, with ancestors fetched only for page context. */
public class ProcessLogTreeDao extends AbstractDao {

    @Inject
    public ProcessLogTreeDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    /* SQL shared by the queries below:
     * WITH RECURSIVE scope AS NOT MATERIALIZED (
     *   SELECT * FROM process_log_segments
     *   WHERE instance_id = :instanceId AND instance_created_at = :createdAt
     * )
     * NOT MATERIALIZED allows indexed probes for children and ancestors.
     */
    private static CommonTableExpression<ProcessLogSegmentsRecord> scope(ProcessKey key) {
        return name("scope").asNotMaterialized(selectFrom(PROCESS_LOG_SEGMENTS)
                .where(PROCESS_LOG_SEGMENTS.INSTANCE_ID.eq(key.getInstanceId()))
                .and(PROCESS_LOG_SEGMENTS.INSTANCE_CREATED_AT.eq(key.getCreatedAt())));
    }

    // CTE references must be unqualified, while columns retain the generated table's types.
    private record SegmentSource(Table<?> table, ProcessLogSegments columns) {
    }

    private static SegmentSource segments(String cte, String alias) {
        return new SegmentSource(table(name(cte)).as(alias), PROCESS_LOG_SEGMENTS.as(alias));
    }

    private static SortField<Long> order(Field<Long> id, boolean reverse) {
        return reverse ? id.desc() : id.asc();
    }

    public LogSegmentTreePage page(ProcessKey key, Long parentId, Long afterId, int limit) {
        var s = segments("scope", "s");
        var where = parentId == null ? s.columns().PARENT_SEGMENT_ID.isNull() : s.columns().PARENT_SEGMENT_ID.eq(parentId);
        if (afterId != null) where = where.and(s.columns().SEGMENT_ID.gt(afterId));
        var found = decoratedPage(key, where, limit, false);
        var items = List.copyOf(found.subList(0, Math.min(limit, found.size())));
        return new LogSegmentTreePage(items, List.of(), found.size() > limit,
                items.isEmpty() ? null : items.getLast().segment().id());
    }

    public LogSegmentTreePage search(ProcessKey key, Long afterId, int limit, String query,
                                     LogSegmentTreeNode.Category category, boolean errorsOnly, boolean reverse) {
        var s = segments("scope", "s");
        Condition where = trueCondition();
        if (afterId != null) where = where.and(reverse ? s.columns().SEGMENT_ID.lt(afterId) : s.columns().SEGMENT_ID.gt(afterId));
        if (query != null && !query.isBlank()) {
            // POSITION is literal substring matching: '%' and '_' are not wildcards.
            where = where.and(position(lower(s.columns().SEGMENT_NAME), lower(val(query.trim()))).gt(0));
        }
        if (category == LogSegmentTreeNode.Category.OK || category == LogSegmentTreeNode.Category.RUNNING) {
            // Neither category can contain FAILED segments, so retry annotations are false.
            if (category == LogSegmentTreeNode.Category.OK) {
                if (errorsOnly) return new LogSegmentTreePage(List.of(), List.of(), false, null);
                where = where.and(s.columns().SEGMENT_STATUS.isNull().or(s.columns().SEGMENT_STATUS.notIn("FAILED", "RUNNING", "SUSPENDED")))
                        .and(coalesce(s.columns().SEGMENT_WARN, 0).le(0)).and(coalesce(s.columns().SEGMENT_ERRORS, 0).le(0));
            } else {
                where = where.and(s.columns().SEGMENT_STATUS.in("RUNNING", "SUSPENDED"));
                if (errorsOnly) where = where.and(s.columns().SEGMENT_ERRORS.gt(0));
            }
            // SELECT s.*, false AS retried, EXISTS(...) AS has_children,
            //        EXISTS(...) AS has_iterations, :category AS category
            // FROM scope s WHERE <stored-value filters> ORDER BY segment_id ASC|DESC LIMIT :limit+1
            var found = dsl().withRecursive(scope(key)).select(s.columns().fields())
                    .select(inline(false).as("retried"),
                            field(hasChildren(s)).as("has_children"), field(hasIterations(s)).as("has_iterations"),
                            val(category.name()).as("category"))
                    .from(s.table()).where(where).orderBy(order(s.columns().SEGMENT_ID, reverse)).limit(limit + 1)
                    .fetch(ProcessLogTreeDao::node);
            return searchPage(key, found, limit);
        }
        if (category == LogSegmentTreeNode.Category.FAILED) {
            where = where.and(s.columns().SEGMENT_STATUS.eq("FAILED"));
        }
        if (category == LogSegmentTreeNode.Category.WARNINGS) {
            where = where.and(s.columns().SEGMENT_STATUS.isNull().or(s.columns().SEGMENT_STATUS.notIn("FAILED", "RUNNING", "SUSPENDED")))
                    .and(s.columns().SEGMENT_WARN.gt(0).or(s.columns().SEGMENT_ERRORS.gt(0)));
        }
        if (errorsOnly) where = where.and(s.columns().SEGMENT_STATUS.eq("FAILED").or(s.columns().SEGMENT_ERRORS.gt(0)));
        return searchPage(key, decoratedPage(key, where, limit, reverse), limit);
    }

    private LogSegmentTreePage searchPage(ProcessKey key, List<LogSegmentTreeNode> found, int limit) {
        var items = List.copyOf(found.subList(0, Math.min(limit, found.size())));
        var ancestors = pathExcluding(key, items.stream().map(n -> n.segment().id()).toList());
        return new LogSegmentTreePage(items, ancestors, found.size() > limit,
                items.isEmpty() ? null : items.getLast().segment().id());
    }

    /* SQL (following scope):
     * , page AS MATERIALIZED (
     *   SELECT s.* FROM scope s WHERE <filters>
     *   ORDER BY segment_id ASC|DESC LIMIT :limit+1
     * ), flags AS (...annotations for page rows...), classified AS (...category...)
     * SELECT * FROM classified ORDER BY segment_id ASC|DESC
     * Select the page first; annotations must not trigger scans for more candidates.
     */
    private List<LogSegmentTreeNode> decoratedPage(ProcessKey key, Condition where, int limit, boolean reverse) {
        var s = segments("scope", "s");
        var page = name("page").asMaterialized(select(s.columns().fields()).from(s.table()).where(where)
                .orderBy(order(s.columns().SEGMENT_ID, reverse)).limit(limit + 1));
        var flags = flags("page", trueCondition());
        var classified = classified();
        return dsl().withRecursive(scope(key), page, flags, classified)
                .selectFrom(classified).orderBy(order(classified.field("segment_id", Long.class), reverse))
                .fetch(ProcessLogTreeDao::node);
    }

    /** Latest/previous descendants in descending creation order; new descendants in ascending order.
     * Ancestors are context only and do not consume the page limit. */
    public LogSegmentTreePage descendants(ProcessKey key, long rootId, Long beforeId, Long afterId, int limit) {
        // Membership is maintained transactionally by segment insert/update/delete
        // triggers. Cursor and LIMIT are index bounds, including on empty polls.
        var membership = table(name("process_log_segment_membership"));
        var id = field(name("segment_id"), Long.class);
        var cursor = beforeId != null ? id.lt(beforeId) : afterId != null ? id.gt(afterId) : trueCondition();
        var found = dsl().select(id).from(membership)
                .where(field(name("instance_id"), UUID.class).eq(key.getInstanceId()))
                .and(field(name("instance_created_at"), OffsetDateTime.class).eq(key.getCreatedAt()))
                .and(field(name("ancestor_id"), Long.class).eq(rootId)).and(id.gt(rootId)).and(cursor)
                .orderBy(order(id, afterId == null)).limit(limit + 1).fetch(id);
        var ids = found.subList(0, Math.min(limit, found.size()));
        var byId = nodes(key, ids).stream().collect(Collectors.toMap(n -> n.segment().id(), n -> n));
        var items = ids.stream().map(byId::get).toList();
        var seeds = new ArrayList<>(ids);
        seeds.add(rootId);
        // IDs increase down each path; ancestors below rootId are outside the selected subtree.
        var ancestors = path(key, seeds).stream()
                .filter(n -> n.segment().id() >= rootId && !byId.containsKey(n.segment().id())).toList();
        return new LogSegmentTreePage(items, ancestors, found.size() > limit, ids.isEmpty() ? null : ids.getLast());
    }

    public List<LogSegmentTreeNode> nodes(ProcessKey key, List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        var s = segments("scope", "s");
        var flags = flags("scope", s.columns().SEGMENT_ID.in(ids));
        var classified = classified();
        return dsl().withRecursive(scope(key), flags, classified).selectFrom(classified)
                .orderBy(classified.field("segment_id", Long.class)).fetch(ProcessLogTreeDao::node);
    }

    public List<LogSegmentTreeNode> path(ProcessKey key, List<Long> ids) {
        return path(key, ids, false);
    }

    private List<LogSegmentTreeNode> pathExcluding(ProcessKey key, List<Long> ids) {
        return path(key, ids, true);
    }

    private List<LogSegmentTreeNode> path(ProcessKey key, List<Long> ids, boolean excludeSeeds) {
        if (ids.isEmpty()) return List.of();
        /* SQL (following scope):
         * , parents(segment_id, parent_segment_id) AS (
         *   SELECT segment_id, parent_segment_id FROM scope WHERE segment_id IN (:ids)
         *   UNION
         *   SELECT p.segment_id, p.parent_segment_id FROM scope p JOIN parents c
         *     ON p.segment_id = c.parent_segment_id AND p.segment_id < c.segment_id
         * ) SELECT segment_id FROM parents ORDER BY segment_id
         * UNION removes ancestors shared by multiple seeds.
         */
        var p = segments("scope", "p");
        var c = table(name("parents")).as("c");
        var parents = name("parents").fields("segment_id", "parent_segment_id").as(
                select(p.columns().SEGMENT_ID, p.columns().PARENT_SEGMENT_ID).from(p.table()).where(p.columns().SEGMENT_ID.in(ids))
                        .union(select(p.columns().SEGMENT_ID, p.columns().PARENT_SEGMENT_ID).from(p.table()).join(c)
                                .on(p.columns().SEGMENT_ID.eq(field(name("c", "parent_segment_id"), Long.class)))
                                .where(p.columns().SEGMENT_ID.lt(field(name("c", "segment_id"), Long.class)))));
        var id = parents.field("segment_id", Long.class);
        var pathIds = dsl().withRecursive(scope(key), parents).select(id).from(parents).orderBy(id).fetch(id);
        if (excludeSeeds) pathIds.removeAll(ids);
        return nodes(key, pathIds);
    }

    /** Resolve the event-bearing execution without downloading all iterations or attempts. */
    public LogSegment run(ProcessKey key, long segmentId) {
        /* SQL (following scope):
         * , runs AS (
         *   SELECT * FROM scope WHERE segment_id = :segmentId
         *   UNION ALL
         *   SELECT child.* FROM runs parent CROSS JOIN LATERAL (
         *     SELECT * FROM (
         *       (SELECT * FROM scope c WHERE <same parent/correlation> AND segment_kind = 'ITERATION'
         *        ORDER BY segment_id LIMIT 1)
         *       UNION ALL
         *       (SELECT * FROM scope c WHERE <same parent/correlation> AND segment_kind = 'ATTEMPT'
         *        ORDER BY segment_iteration_index ASC NULLS FIRST, segment_attempt_index DESC, segment_id DESC LIMIT 1)
         *     ) choices ORDER BY segment_id LIMIT 1
         *   ) child
         * ) SELECT segment_id FROM runs ORDER BY segment_id DESC LIMIT 1
         */
        var s = segments("scope", "s");
        var c = segments("scope", "c");
        var parent = segments("runs", "parent");
        var sameRun = c.columns().PARENT_SEGMENT_ID.eq(parent.columns().SEGMENT_ID).and(c.columns().SEGMENT_ID.gt(parent.columns().SEGMENT_ID))
                .and(c.columns().CORRELATION_ID.eq(parent.columns().CORRELATION_ID));
        var iteration = select(c.columns().fields()).from(c.table()).where(sameRun).and(c.columns().SEGMENT_KIND.eq(ITERATION)).orderBy(c.columns().SEGMENT_ID).limit(1);
        var attempt = select(c.columns().fields()).from(c.table()).where(sameRun).and(c.columns().SEGMENT_KIND.eq(ATTEMPT))
                .orderBy(c.columns().SEGMENT_ITERATION_INDEX.asc().nullsFirst(), c.columns().SEGMENT_ATTEMPT_INDEX.desc(), c.columns().SEGMENT_ID.desc()).limit(1);
        var choices = iteration.unionAll(attempt).asTable("choices");
        var child = lateral(selectFrom(choices).orderBy(choices.field(c.columns().SEGMENT_ID)).limit(1)).as("child");
        var runs = name("runs").as(select(s.columns().fields()).from(s.table()).where(s.columns().SEGMENT_ID.eq(segmentId))
                .unionAll(select(child.fields()).from(parent.table()).crossJoin(child)));
        var id = runs.field("segment_id", Long.class);
        var result = dsl().withRecursive(scope(key), runs).select(id).from(runs).orderBy(id.desc()).limit(1).fetchOne(id);
        return result == null ? null : nodes(key, List.of(result)).getFirst().segment();
    }

    private static Condition retried(SegmentSource s) {
        var sibling = segments("scope", "sibling");
        // PostgreSQL 14 cannot use IS NOT DISTINCT FROM as an index equality.
        var later = sibling.columns().PARENT_SEGMENT_ID.eq(s.columns().PARENT_SEGMENT_ID)
                .and(sibling.columns().SEGMENT_KIND.eq(inline(ATTEMPT)))
                .and(sibling.columns().SEGMENT_ATTEMPT_INDEX.gt(s.columns().SEGMENT_ATTEMPT_INDEX));
        var sameIteration = select(sibling.columns().SEGMENT_ID).from(sibling.table()).where(later)
                .and(sibling.columns().SEGMENT_ITERATION_INDEX.eq(s.columns().SEGMENT_ITERATION_INDEX))
                .orderBy(sibling.columns().SEGMENT_ATTEMPT_INDEX, sibling.columns().SEGMENT_ID).limit(1).asField();
        var noIteration = select(sibling.columns().SEGMENT_ID).from(sibling.table()).where(later)
                .and(sibling.columns().SEGMENT_ITERATION_INDEX.isNull())
                .orderBy(sibling.columns().SEGMENT_ATTEMPT_INDEX, sibling.columns().SEGMENT_ID).limit(1).asField();
        return s.columns().SEGMENT_STATUS.eq("FAILED").and(s.columns().SEGMENT_KIND.eq(ATTEMPT))
                .and(when(s.columns().SEGMENT_ITERATION_INDEX.isNull(), noIteration).otherwise(sameIteration).isNotNull());
    }

    private static Condition hasChildren(SegmentSource s) {
        var c = segments("scope", "c");
        // Seek by parent, then compare the first key. Equality + ORDER BY segment_id
        // can choose the global ID index and filter every row for a missing parent.
        return select(c.columns().PARENT_SEGMENT_ID).from(c.table())
                .where(c.columns().PARENT_SEGMENT_ID.ge(s.columns().SEGMENT_ID))
                .orderBy(c.columns().PARENT_SEGMENT_ID, c.columns().SEGMENT_ID)
                .limit(1).<Long>asField().isNotDistinctFrom(s.columns().SEGMENT_ID);
    }

    private static Condition hasIterations(SegmentSource s) {
        var c = segments("scope", "c");
        var parent = c.columns().PARENT_SEGMENT_ID.eq(s.columns().SEGMENT_ID);
        var iteration = select(c.columns().PARENT_SEGMENT_ID).from(c.table())
                .where(c.columns().PARENT_SEGMENT_ID.ge(s.columns().SEGMENT_ID))
                .and(c.columns().SEGMENT_KIND.eq(inline(ITERATION)))
                .orderBy(c.columns().PARENT_SEGMENT_ID, c.columns().SEGMENT_ID).limit(1).<Long>asField();
        var attempt = select(c.columns().SEGMENT_ID).from(c.table()).where(parent)
                .and(c.columns().SEGMENT_KIND.eq(inline(ATTEMPT))).and(c.columns().SEGMENT_ITERATION_INDEX.isNotNull())
                .orderBy(c.columns().SEGMENT_ITERATION_INDEX, c.columns().SEGMENT_ATTEMPT_INDEX, c.columns().SEGMENT_ID)
                .limit(1).asField();
        return iteration.isNotDistinctFrom(s.columns().SEGMENT_ID).or(attempt.isNotNull());
    }

    /* SQL:
     * flags AS (SELECT s.*, COALESCE(<retried(s)>, false) AS retried,
     *   <ordered child probe> IS NOT NULL AS has_children,
     *   <iteration/attempt probes> IS NOT NULL AS has_iterations FROM scope|page s WHERE <filter>)
     */
    private static CommonTableExpression<Record> flags(String source, Condition where) {
        var s = segments(source, "s");
        return name("flags").as(select(s.columns().fields())
                .select(coalesce(field(retried(s)), false).as("retried"),
                        field(hasChildren(s)).as("has_children"), field(hasIterations(s)).as("has_iterations"))
                .from(s.table()).where(where));
    }

    /* SQL:
     * classified AS (SELECT flags.*, CASE
     *   WHEN segment_status = 'FAILED' THEN 'FAILED'
     *   WHEN segment_status IN ('RUNNING', 'SUSPENDED') THEN 'RUNNING'
     *   WHEN segment_warn > 0 OR segment_errors > 0 THEN 'WARNINGS'
     *   ELSE 'OK' END AS category FROM flags)
     */
    private static CommonTableExpression<Record> classified() {
        var f = segments("flags", "f");
        var category = when(f.columns().SEGMENT_STATUS.eq("FAILED"), "FAILED")
                .when(f.columns().SEGMENT_STATUS.in("RUNNING", "SUSPENDED"), "RUNNING")
                .when(f.columns().SEGMENT_WARN.gt(0).or(f.columns().SEGMENT_ERRORS.gt(0)), "WARNINGS")
                .otherwise("OK").as("category");
        return name("classified").as(select(f.columns().fields()).select(
                field(name("f", "retried"), Boolean.class),
                field(name("f", "has_children"), Boolean.class), field(name("f", "has_iterations"), Boolean.class),
                category).from(f.table()));
    }

    private static LogSegmentTreeNode node(Record row) {
        var kind = row.get("segment_kind", String.class);
        var status = row.get("segment_status", String.class);
        var segment = LogSegment.builder()
                .id(row.get("segment_id", Long.class))
                .name(row.get("segment_name", String.class))
                .correlationId(row.get("correlation_id", UUID.class))
                .parentId(row.get("parent_segment_id", Long.class))
                .kind(kind == null ? null : LogSegment.Kind.valueOf(kind))
                .iterationIndex(row.get("segment_iteration_index", Integer.class))
                .attemptIndex(row.get("segment_attempt_index", Integer.class))
                .threadId(row.get("segment_thread", Integer.class))
                .status(status == null ? null : LogSegment.Status.valueOf(status))
                .createdAt(row.get("segment_ts", OffsetDateTime.class))
                .statusUpdatedAt(row.get("status_updated_at", OffsetDateTime.class))
                .warnings(row.get("segment_warn", Integer.class))
                .errors(row.get("segment_errors", Integer.class))
                .build();
        return new LogSegmentTreeNode(segment, row.get("has_children", Boolean.class), row.get("has_iterations", Boolean.class),
                row.get("retried", Boolean.class),
                LogSegmentTreeNode.Category.valueOf(row.get("category", String.class)));
    }
}
