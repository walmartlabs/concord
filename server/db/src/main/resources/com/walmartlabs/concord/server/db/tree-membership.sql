-- Installation is atomic with backfill: no segment writes may race the snapshot.
-- The DEFAULT partition accepts every process date; range partitions can be split
-- out by the installation's normal retention/provisioning tooling. Parent indexes
-- automatically provision indexes on every future partition (PostgreSQL 14).
SET LOCAL lock_timeout = '5s';
LOCK TABLE process_log_segments IN SHARE ROW EXCLUSIVE MODE;
CREATE TABLE process_log_segment_membership (
    instance_id uuid NOT NULL,
    instance_created_at timestamptz NOT NULL,
    ancestor_id bigint NOT NULL,
    segment_id bigint NOT NULL,
    PRIMARY KEY (instance_id, instance_created_at, ancestor_id, segment_id)
) PARTITION BY RANGE (instance_created_at);
CREATE TABLE process_log_segment_membership_default
    PARTITION OF process_log_segment_membership DEFAULT;
CREATE INDEX idx_proc_log_membership_segment
    ON process_log_segment_membership(instance_id, instance_created_at, segment_id, ancestor_id);

-- Include self rows to make an insertion's descendants and ancestor chains
-- index-addressable. Queries exclude self with the same increasing-ID guard as
-- the former traversal. A dangling parent remains an addressable subtree root.
WITH RECURSIVE membership(instance_id, instance_created_at, ancestor_id, segment_id) AS (
    SELECT instance_id, instance_created_at, segment_id, segment_id
    FROM process_log_segments
    WHERE instance_id IS NOT NULL AND instance_created_at IS NOT NULL
    UNION ALL
    SELECT c.instance_id, c.instance_created_at, c.parent_segment_id, m.segment_id
    FROM membership m JOIN process_log_segments c
      ON c.instance_id = m.instance_id AND c.instance_created_at = m.instance_created_at
     AND c.segment_id = m.ancestor_id
    WHERE c.parent_segment_id < c.segment_id
)
INSERT INTO process_log_segment_membership
SELECT DISTINCT * FROM membership;

CREATE FUNCTION lock_process_log_segment_membership() RETURNS trigger
LANGUAGE plpgsql AS $lock$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.instance_id::text, 2461050));
    RETURN NEW;
END
$lock$;

CREATE FUNCTION maintain_process_log_segment_membership() RETURNS trigger
LANGUAGE plpgsql AS $membership$
BEGIN
    IF TG_OP <> 'DELETE' AND (NEW.instance_id IS NULL OR NEW.instance_created_at IS NULL) THEN
        RETURN NEW;
    END IF;
    IF TG_OP <> 'INSERT' THEN
        PERFORM pg_advisory_xact_lock(hashtextextended(OLD.instance_id::text, 2461050));
        DELETE FROM process_log_segment_membership m
        USING process_log_segment_membership a, process_log_segment_membership d
        WHERE a.instance_id = OLD.instance_id AND a.instance_created_at = OLD.instance_created_at
          AND a.segment_id = OLD.segment_id AND a.ancestor_id < OLD.segment_id
          AND d.instance_id = OLD.instance_id AND d.instance_created_at = OLD.instance_created_at
          AND d.ancestor_id = OLD.segment_id
          AND m.instance_id = OLD.instance_id AND m.instance_created_at = OLD.instance_created_at
          AND m.ancestor_id = a.ancestor_id AND m.segment_id = d.segment_id;
        DELETE FROM process_log_segment_membership
        WHERE instance_id = OLD.instance_id AND instance_created_at = OLD.instance_created_at
          AND segment_id = OLD.segment_id;
        IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    END IF;

        -- One statement handles normal creation and out-of-order parent arrival.
        -- Climb only valid edges in this process/date, never another process.
        WITH RECURSIVE ancestors(id) AS (
            SELECT NEW.segment_id
            UNION ALL
            SELECT p.parent_segment_id
            FROM ancestors a JOIN process_log_segments p
              ON p.instance_id = NEW.instance_id AND p.instance_created_at = NEW.instance_created_at
             AND p.segment_id = a.id
            WHERE p.parent_segment_id < p.segment_id
        ), descendants(id) AS (
            SELECT NEW.segment_id
            UNION
            SELECT segment_id FROM process_log_segment_membership
            WHERE instance_id = NEW.instance_id AND instance_created_at = NEW.instance_created_at
              AND ancestor_id = NEW.segment_id
        )
        INSERT INTO process_log_segment_membership(instance_id, instance_created_at, ancestor_id, segment_id)
        SELECT NEW.instance_id, NEW.instance_created_at, a.id, d.id
        FROM ancestors a CROSS JOIN descendants d
        ON CONFLICT DO NOTHING;
    RETURN NEW;
END
$membership$;
-- Declarative partition triggers are cloned by PostgreSQL. Legacy INHERITS
-- children are ordinary tables and need their own triggers. Operators creating
-- future empty inheritance children must call this routine in the same creation
-- transaction, before allowing writes; it is safe to call repeatedly.
CREATE FUNCTION provision_process_log_segment_membership() RETURNS void
LANGUAGE plpgsql AS $provision$
DECLARE
    target record;
    trigger_spec record;
    function_schema text;
BEGIN
    SELECT n.nspname INTO function_schema
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.oid = 'process_log_segments'::regclass;
    FOR target IN
        WITH RECURSIVE descendants(oid, depth) AS (
            SELECT 'process_log_segments'::regclass::oid, 0
            UNION ALL
            SELECT i.inhrelid, d.depth + 1 FROM descendants d
            JOIN pg_inherits i ON i.inhparent = d.oid
        )
        SELECT c.oid, min(d.depth) AS depth
        FROM descendants d JOIN pg_class c ON c.oid = d.oid
        WHERE NOT c.relispartition AND c.relkind IN ('r', 'p')
        GROUP BY c.oid ORDER BY min(d.depth), c.oid
    LOOP
        FOR trigger_spec IN
            SELECT * FROM (VALUES
                ('process_log_segment_membership_lock',
                 'BEFORE INSERT OR UPDATE OF parent_segment_id',
                 'lock_process_log_segment_membership'),
                ('process_log_segment_membership_insert',
                 'AFTER INSERT OR UPDATE OF parent_segment_id',
                 'maintain_process_log_segment_membership'),
                ('process_log_segment_membership_delete',
                 'BEFORE DELETE',
                 'maintain_process_log_segment_membership')
            ) AS specs(trigger_name, events, function_name)
        LOOP
            IF NOT EXISTS (
                SELECT 1 FROM pg_trigger t
                WHERE t.tgrelid = target.oid AND t.tgname = trigger_spec.trigger_name
            ) THEN
                EXECUTE format('CREATE TRIGGER %I %s ON %s FOR EACH ROW EXECUTE FUNCTION %I.%I()',
                    trigger_spec.trigger_name, trigger_spec.events, target.oid::regclass,
                    function_schema, trigger_spec.function_name);
            END IF;
        END LOOP;
    END LOOP;
END
$provision$;
SELECT provision_process_log_segment_membership();
