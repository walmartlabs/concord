--
-- PostgreSQL database dump
--



SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';


--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


--
-- Name: out_variables_mode; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.out_variables_mode AS ENUM (
    'DISABLED',
    'OWNERS',
    'TEAM_MEMBERS',
    'ORG_MEMBERS',
    'EVERYONE'
);


--
-- Name: process_exec_mode; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.process_exec_mode AS ENUM (
    'DISABLED',
    'READERS',
    'WRITERS'
);


--
-- Name: process_lock_scope; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.process_lock_scope AS ENUM (
    'ORG',
    'PROJECT'
);


--
-- Name: raw_payload_mode; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.raw_payload_mode AS ENUM (
    'DISABLED',
    'OWNERS',
    'TEAM_MEMBERS',
    'ORG_MEMBERS',
    'EVERYONE'
);


--
-- Name: task_status_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.task_status_type AS ENUM (
    'OK',
    'ERROR',
    'RUNNING',
    'STALLED'
);


--
-- Name: process_log_data_last_n_bytes(uuid, timestamp without time zone, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.process_log_data_last_n_bytes(p_instance_id uuid, p_created_at timestamp without time zone, p_data_len integer) RETURNS int4range
    LANGUAGE plpgsql
    AS $$
            declare
                R_START int;
            begin
                select coalesce(max(upper(LOG_RANGE)), 0) into R_START
                from PROCESS_LOG_DATA
                where
                    INSTANCE_ID = P_INSTANCE_ID and INSTANCE_CREATED_AT = P_CREATED_AT;

                if R_START is null then
                    R_START := 0;
                end if;

                return int4range(R_START - P_DATA_LEN, R_START);
            end;
            $$;


--
-- Name: process_log_data_last_n_bytes(uuid, timestamp with time zone, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.process_log_data_last_n_bytes(p_instance_id uuid, p_created_at timestamp with time zone, p_data_len integer) RETURNS int4range
    LANGUAGE plpgsql
    AS $$
            declare
                R_START int;
            begin
                select coalesce(max(upper(LOG_RANGE)), 0) into R_START
                from PROCESS_LOG_DATA
                where
                INSTANCE_ID = P_INSTANCE_ID and INSTANCE_CREATED_AT = P_CREATED_AT;

                if R_START is null then
                    R_START := 0;
                end if;

                return int4range(R_START - P_DATA_LEN, R_START);
            end;
            $$;


--
-- Name: process_log_data_next_range(uuid, timestamp without time zone, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.process_log_data_next_range(p_instance_id uuid, p_created_at timestamp without time zone, p_data_len integer) RETURNS int4range
    LANGUAGE plpgsql
    AS $$
            declare
            R_START int;
            begin
                select coalesce(max(upper(LOG_RANGE)), 0) into R_START
                from PROCESS_LOG_DATA
                where
                    INSTANCE_ID = P_INSTANCE_ID and INSTANCE_CREATED_AT = P_CREATED_AT;

                if R_START is null then
                    R_START := 0;
                end if;

                return int4range(R_START, R_START + P_DATA_LEN);
            end;
            $$;


--
-- Name: process_log_data_next_range(uuid, timestamp with time zone, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.process_log_data_next_range(p_instance_id uuid, p_created_at timestamp with time zone, p_data_len integer) RETURNS int4range
    LANGUAGE plpgsql
    AS $$
            declare
                R_START int;
            begin
                select coalesce(max(upper(LOG_RANGE)), 0) into R_START
                from PROCESS_LOG_DATA
                where
                INSTANCE_ID = P_INSTANCE_ID and INSTANCE_CREATED_AT = P_CREATED_AT;

                if R_START is null then
                    R_START := 0;
                end if;

                return int4range(R_START, R_START + P_DATA_LEN);
            end;
            $$;


--
-- Name: process_log_data_segment_last_n_bytes(uuid, timestamp without time zone, bigint, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.process_log_data_segment_last_n_bytes(p_instance_id uuid, p_created_at timestamp without time zone, p_segment_id bigint, p_data_len integer) RETURNS int4range
    LANGUAGE plpgsql
    AS $$
            declare
                R_START int;
            begin
                select coalesce(max(upper(SEGMENT_RANGE)), 0) into R_START
                from PROCESS_LOG_DATA
                where
                    INSTANCE_ID = P_INSTANCE_ID and INSTANCE_CREATED_AT = P_CREATED_AT and SEGMENT_ID = P_SEGMENT_ID;

                if R_START is null then
                    R_START := 0;
                end if;

                return int4range(R_START - P_DATA_LEN, R_START);
            end;
            $$;


--
-- Name: process_log_data_segment_last_n_bytes(uuid, timestamp with time zone, bigint, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.process_log_data_segment_last_n_bytes(p_instance_id uuid, p_created_at timestamp with time zone, p_segment_id bigint, p_data_len integer) RETURNS int4range
    LANGUAGE plpgsql
    AS $$
            declare
                R_START int;
            begin
                select coalesce(max(upper(SEGMENT_RANGE)), 0) into R_START
                from PROCESS_LOG_DATA
                where
                INSTANCE_ID = P_INSTANCE_ID and INSTANCE_CREATED_AT = P_CREATED_AT and SEGMENT_ID = P_SEGMENT_ID;

                if R_START is null then
                R_START := 0;
                end if;

                return int4range(R_START - P_DATA_LEN, R_START);
            end;
            $$;


--
-- Name: process_log_data_segment_next_range(uuid, timestamp without time zone, bigint, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.process_log_data_segment_next_range(p_instance_id uuid, p_created_at timestamp without time zone, p_segment_id bigint, p_data_len integer) RETURNS int4range
    LANGUAGE plpgsql
    AS $$
            declare
                R_START int;
            begin
                select coalesce(max(upper(SEGMENT_RANGE)), 0) into R_START
                from PROCESS_LOG_DATA
                where
                    INSTANCE_ID = P_INSTANCE_ID and INSTANCE_CREATED_AT = P_CREATED_AT and SEGMENT_ID = P_SEGMENT_ID;

                if R_START is null then
                    R_START := 0;
                end if;

                return int4range(R_START, R_START + P_DATA_LEN);
            end;
            $$;


--
-- Name: process_log_data_segment_next_range(uuid, timestamp with time zone, bigint, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.process_log_data_segment_next_range(p_instance_id uuid, p_created_at timestamp with time zone, p_segment_id bigint, p_data_len integer) RETURNS int4range
    LANGUAGE plpgsql
    AS $$
            declare
                R_START int;
            begin
                select coalesce(max(upper(SEGMENT_RANGE)), 0) into R_START
                from PROCESS_LOG_DATA
                where
                INSTANCE_ID = P_INSTANCE_ID and INSTANCE_CREATED_AT = P_CREATED_AT and SEGMENT_ID = P_SEGMENT_ID;

                if R_START is null then
                    R_START := 0;
                end if;

                return int4range(R_START, R_START + P_DATA_LEN);
            end;
            $$;


--
-- Name: ts_to_tstz(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.ts_to_tstz(t text) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
            declare
                v_cnt numeric;
            begin
                v_cnt := 0;

                update pg_attribute
                    set atttypid = 'timestamp with time zone'::regtype
                from pg_class
                where attrelid = pg_class.oid
                    and relnamespace = current_schema()::regnamespace
                    and atttypid = 'timestamp'::regtype
                    and relname ilike t;

                get diagnostics v_cnt = row_count;
                if v_cnt = 0 then
                    raise warning 'Relation not found (or is already converted): %', t;
                end if;

                update pg_index
                    set indclass = array_to_string(array_replace(indclass::oid[], 3128::oid, 3127::oid), ' ')::oidvector
                from pg_class
                where indrelid = pg_class.oid
                    and relnamespace = current_schema()::regnamespace
                    and indclass::oid[] @> ARRAY[3128::oid]
                    and relname ilike t;

                return v_cnt > 0;
            end;
            $$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: admins; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admins (
    user_id uuid NOT NULL
);


--
-- Name: agent_commands; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_commands (
    command_id uuid NOT NULL,
    agent_id character varying(36) NOT NULL,
    command_status character varying(32) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    command_data bytea NOT NULL
);


--
-- Name: api_keys; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.api_keys (
    key_id uuid DEFAULT public.uuid_generate_v1() NOT NULL,
    api_key character varying(64) NOT NULL,
    user_id uuid,
    key_name character varying(128) DEFAULT 'n/a'::character varying NOT NULL,
    expired_at timestamp with time zone,
    last_notified_at timestamp with time zone
);


--
-- Name: TABLE api_keys; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.api_keys IS 'API access keys';


--
-- Name: COLUMN api_keys.key_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_keys.key_id IS 'Unique key ID';


--
-- Name: COLUMN api_keys.api_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_keys.api_key IS 'SHA-256 hash of a key';


--
-- Name: COLUMN api_keys.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_keys.user_id IS 'ID of a key''s user';


--
-- Name: audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_log (
    entry_date timestamp with time zone DEFAULT now() NOT NULL,
    user_id uuid,
    entry_object character varying(128) NOT NULL,
    entry_action character varying(128) NOT NULL,
    entry_details jsonb,
    entry_seq bigint NOT NULL
);


--
-- Name: COLUMN audit_log.entry_seq; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.audit_log.entry_seq IS 'Add sequences to enable forwarding';


--
-- Name: audit_log_entry_seq_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.audit_log ALTER COLUMN entry_seq ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.audit_log_entry_seq_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: event_processor_marker; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_processor_marker (
    processor_name character varying(64) NOT NULL,
    event_seq bigint NOT NULL
);


--
-- Name: external_app_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.external_app_users (
    external_user_id character varying(512) NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: json_stores; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.json_stores (
    json_store_id uuid DEFAULT public.uuid_generate_v1() NOT NULL,
    json_store_name character varying(128) NOT NULL,
    parent_inventory_id uuid,
    org_id uuid DEFAULT '0fac1b18-d179-11e7-b3e7-d7df4543ed4f'::uuid NOT NULL,
    visibility character varying(128) DEFAULT 'PUBLIC'::character varying NOT NULL,
    owner_id uuid
);


--
-- Name: TABLE json_stores; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.json_stores IS 'Inventories';


--
-- Name: COLUMN json_stores.json_store_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.json_stores.json_store_id IS 'Unique ID of the inventory';


--
-- Name: COLUMN json_stores.json_store_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.json_stores.json_store_name IS 'Unique name of the inventory';


--
-- Name: COLUMN json_stores.parent_inventory_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.json_stores.parent_inventory_id IS 'ID of the parent inventory';


--
-- Name: inventories; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.inventories AS
 SELECT json_stores.json_store_id AS inventory_id,
    json_stores.json_store_name AS inventory_name,
    NULL::text AS parent_inventory_id,
    json_stores.org_id,
    json_stores.visibility,
    json_stores.owner_id
   FROM public.json_stores;


--
-- Name: json_store_data; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.json_store_data (
    json_store_id uuid NOT NULL,
    item_path character varying(1024) NOT NULL,
    item_data jsonb NOT NULL,
    item_data_size bigint
);


--
-- Name: TABLE json_store_data; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.json_store_data IS 'Inventory data';


--
-- Name: COLUMN json_store_data.json_store_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.json_store_data.json_store_id IS 'FK to an inventory';


--
-- Name: COLUMN json_store_data.item_path; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.json_store_data.item_path IS 'Unique (for an inventory) path to an entry';


--
-- Name: COLUMN json_store_data.item_data; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.json_store_data.item_data IS 'JSON data';


--
-- Name: json_store_data_view_restricted; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.json_store_data_view_restricted AS
 SELECT json_store_data.json_store_id,
    json_store_data.item_path,
    json_store_data.item_data,
    json_store_data.item_data_size
   FROM public.json_store_data
  WHERE (json_store_data.json_store_id = (current_setting('jsonStoreQueryExec.json_store_id'::text))::uuid);


--
-- Name: inventory_data; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.inventory_data AS
 SELECT json_store_data_view_restricted.json_store_id AS inventory_id,
    json_store_data_view_restricted.item_path,
    json_store_data_view_restricted.item_data
   FROM public.json_store_data_view_restricted;


--
-- Name: json_store_queries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.json_store_queries (
    query_id uuid DEFAULT public.uuid_generate_v1() NOT NULL,
    json_store_id uuid NOT NULL,
    query_name character varying(256) NOT NULL,
    query_text character varying(4000) NOT NULL
);


--
-- Name: TABLE json_store_queries; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.json_store_queries IS 'Inventory queries';


--
-- Name: COLUMN json_store_queries.query_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.json_store_queries.query_id IS 'Unique query index';


--
-- Name: COLUMN json_store_queries.json_store_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.json_store_queries.json_store_id IS 'FK to an inventory';


--
-- Name: COLUMN json_store_queries.query_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.json_store_queries.query_name IS 'Unique (for an inventory) query name';


--
-- Name: COLUMN json_store_queries.query_text; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.json_store_queries.query_text IS 'Query text';


--
-- Name: inventory_queries; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.inventory_queries AS
 SELECT json_store_queries.query_id,
    json_store_queries.json_store_id AS inventory_id,
    json_store_queries.query_name,
    json_store_queries.query_text
   FROM public.json_store_queries;


--
-- Name: json_store_team_access; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.json_store_team_access (
    json_store_id uuid NOT NULL,
    team_id uuid NOT NULL,
    access_level character varying(128) DEFAULT 'READER'::character varying NOT NULL
);


--
-- Name: organizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organizations (
    org_id uuid DEFAULT public.uuid_generate_v1() NOT NULL,
    org_name character varying(128) NOT NULL,
    visibility character varying(128) DEFAULT 'PUBLIC'::character varying NOT NULL,
    meta jsonb,
    org_cfg jsonb,
    owner_id uuid
);


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions (
    permission_id uuid DEFAULT public.uuid_generate_v1() NOT NULL,
    permission_name character varying(256) NOT NULL,
    description character varying(1024) NOT NULL
);


--
-- Name: TABLE permissions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.permissions IS 'Dictionary of permissions';


--
-- Name: policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.policies (
    policy_id uuid DEFAULT public.uuid_generate_v1() NOT NULL,
    policy_name character varying(256) NOT NULL,
    rules jsonb NOT NULL,
    parent_policy_id uuid
);


--
-- Name: policy_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.policy_links (
    org_id uuid,
    project_id uuid,
    policy_id uuid NOT NULL,
    user_id uuid
);


--
-- Name: process_checkpoints; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.process_checkpoints (
    checkpoint_id uuid NOT NULL,
    instance_id uuid NOT NULL,
    checkpoint_data bytea NOT NULL,
    checkpoint_name character varying(128),
    checkpoint_date timestamp with time zone,
    instance_created_at timestamp with time zone NOT NULL,
    correlation_id uuid
);


--
-- Name: COLUMN process_checkpoints.instance_created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_checkpoints.instance_created_at IS 'Same as PROCESS_QUEUE.CREATED_AT';


--
-- Name: process_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.process_events (
    instance_id uuid NOT NULL,
    event_type character varying(36) NOT NULL,
    event_date timestamp with time zone NOT NULL,
    event_data jsonb NOT NULL,
    event_id uuid DEFAULT public.uuid_generate_v1() NOT NULL,
    event_seq bigint NOT NULL,
    instance_created_at timestamp with time zone NOT NULL
);


--
-- Name: COLUMN process_events.event_seq; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_events.event_seq IS 'Add sequences to enable forwarding';


--
-- Name: process_events_event_seq_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.process_events ALTER COLUMN event_seq ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.process_events_event_seq_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: process_initial_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.process_initial_state (
    instance_id uuid NOT NULL,
    instance_created_at timestamp with time zone NOT NULL,
    item_path character varying(2048) NOT NULL,
    item_data bytea NOT NULL,
    is_encrypted boolean DEFAULT false NOT NULL,
    unix_mode numeric(4,0) DEFAULT 420 NOT NULL
);


--
-- Name: COLUMN process_initial_state.instance_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_initial_state.instance_id IS 'Unique process ID';


--
-- Name: COLUMN process_initial_state.instance_created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_initial_state.instance_created_at IS 'Timestamp of process creation';


--
-- Name: process_locks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.process_locks (
    instance_id uuid NOT NULL,
    org_id uuid NOT NULL,
    project_id uuid NOT NULL,
    lock_scope public.process_lock_scope NOT NULL,
    lock_name character varying(128) NOT NULL
);


--
-- Name: process_log_data; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.process_log_data (
    instance_id uuid NOT NULL,
    instance_created_at timestamp with time zone NOT NULL,
    segment_id bigint NOT NULL,
    log_range int4range NOT NULL,
    segment_range int4range NOT NULL,
    chunk_data bytea NOT NULL,
    log_seq bigint NOT NULL
);


--
-- Name: process_log_data_log_seq_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.process_log_data ALTER COLUMN log_seq ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.process_log_data_log_seq_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: process_log_data_segment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.process_log_data ALTER COLUMN segment_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.process_log_data_segment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: process_log_segments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.process_log_segments (
    instance_id uuid NOT NULL,
    instance_created_at timestamp with time zone NOT NULL,
    segment_id bigint NOT NULL,
    segment_name text NOT NULL,
    correlation_id uuid,
    segment_ts timestamp with time zone NOT NULL,
    segment_status text,
    segment_errors integer,
    segment_warn integer,
    status_updated_at timestamp with time zone
);


--
-- Name: process_log_segments_segment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.process_log_segments ALTER COLUMN segment_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.process_log_segments_segment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: process_meta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.process_meta (
    instance_id uuid NOT NULL,
    instance_created_at timestamp with time zone NOT NULL,
    meta jsonb
);


--
-- Name: COLUMN process_meta.instance_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_meta.instance_id IS 'Unique process ID';


--
-- Name: COLUMN process_meta.instance_created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_meta.instance_created_at IS 'Timestamp of process creation';


--
-- Name: process_queue; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.process_queue (
    instance_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    current_status character varying(32) NOT NULL,
    last_agent_id character varying(128),
    last_updated_at timestamp with time zone NOT NULL,
    parent_instance_id uuid,
    process_kind character varying(128),
    process_tags text[],
    project_id uuid,
    start_at timestamp with time zone,
    requirements jsonb,
    repo_id uuid,
    repo_url character varying(2048),
    repo_path character varying(2048),
    commit_id character varying(64),
    initiator_id uuid,
    timeout bigint,
    handlers text[],
    last_run_at timestamp with time zone,
    wait_conditions jsonb,
    is_disabled boolean DEFAULT false NOT NULL,
    imports jsonb,
    exclusive jsonb,
    runtime text,
    id_seq bigint NOT NULL,
    dependencies text[],
    commit_branch character varying(255),
    suspend_timeout bigint,
    total_runtime_ms bigint
);


--
-- Name: COLUMN process_queue.instance_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_queue.instance_id IS 'Unique process ID';


--
-- Name: COLUMN process_queue.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_queue.created_at IS 'Timestamp of process creation';


--
-- Name: COLUMN process_queue.current_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_queue.current_status IS 'Current status of a process';


--
-- Name: COLUMN process_queue.last_agent_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_queue.last_agent_id IS 'ID of the last agent that was executing the process';


--
-- Name: COLUMN process_queue.last_updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_queue.last_updated_at IS 'Timestamp of the last update';


--
-- Name: COLUMN process_queue.timeout; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_queue.timeout IS 'Timeout (in seconds)';


--
-- Name: COLUMN process_queue.handlers; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_queue.handlers IS 'List of process handlers';


--
-- Name: process_queue_id_seq_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.process_queue ALTER COLUMN id_seq ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.process_queue_id_seq_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: process_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.process_state (
    instance_id uuid NOT NULL,
    item_path character varying(2048) NOT NULL,
    item_data bytea NOT NULL,
    unix_mode numeric(4,0) DEFAULT 420 NOT NULL,
    is_encrypted boolean DEFAULT false NOT NULL,
    instance_created_at timestamp with time zone NOT NULL
);


--
-- Name: COLUMN process_state.instance_created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_state.instance_created_at IS 'Same as PROCESS_QUEUE.CREATED_AT';


--
-- Name: process_trigger_info; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.process_trigger_info (
    instance_id uuid NOT NULL,
    instance_created_at timestamp with time zone NOT NULL,
    triggered_by jsonb
);


--
-- Name: COLUMN process_trigger_info.instance_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_trigger_info.instance_id IS 'Unique process ID';


--
-- Name: COLUMN process_trigger_info.instance_created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.process_trigger_info.instance_created_at IS 'Timestamp of process creation';


--
-- Name: process_wait_conditions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.process_wait_conditions (
    instance_id uuid NOT NULL,
    instance_created_at timestamp with time zone NOT NULL,
    is_waiting boolean DEFAULT false NOT NULL,
    wait_conditions jsonb,
    id_seq bigint NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: process_wait_conditions_id_seq_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.process_wait_conditions ALTER COLUMN id_seq ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.process_wait_conditions_id_seq_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: project_kv_store; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_kv_store (
    value_key character varying(128) NOT NULL,
    value_long numeric(16,0),
    value_string character varying(1024),
    project_id uuid DEFAULT '00000000-0000-0000-0000-000000000000'::uuid NOT NULL,
    last_updated_at timestamp with time zone
);


--
-- Name: TABLE project_kv_store; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.project_kv_store IS 'KV store';


--
-- Name: project_secrets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_secrets (
    secret_id uuid NOT NULL,
    project_id uuid NOT NULL
);


--
-- Name: project_team_access; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_team_access (
    project_id uuid NOT NULL,
    team_id uuid NOT NULL,
    access_level character varying(128) DEFAULT 'READER'::character varying NOT NULL
);


--
-- Name: projects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projects (
    project_name character varying(128) NOT NULL,
    description character varying(1024),
    project_cfg jsonb,
    project_id uuid DEFAULT public.uuid_generate_v1() NOT NULL,
    visibility character varying(128) DEFAULT 'PUBLIC'::character varying NOT NULL,
    org_id uuid DEFAULT '0fac1b18-d179-11e7-b3e7-d7df4543ed4f'::uuid NOT NULL,
    owner_id uuid,
    secret_key bytea,
    meta jsonb,
    raw_payload_mode public.raw_payload_mode DEFAULT 'DISABLED'::public.raw_payload_mode NOT NULL,
    out_variables_mode public.out_variables_mode DEFAULT 'DISABLED'::public.out_variables_mode NOT NULL,
    created_at timestamp with time zone,
    process_exec_mode public.process_exec_mode DEFAULT 'READERS'::public.process_exec_mode NOT NULL
);


--
-- Name: COLUMN projects.project_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.projects.project_name IS 'Name (key) of a project';


--
-- Name: repositories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.repositories (
    repo_name character varying(128) NOT NULL,
    repo_url character varying(2048) NOT NULL,
    repo_branch character varying(255),
    repo_commit_id character varying(64),
    repo_path character varying(2048),
    project_id uuid NOT NULL,
    repo_id uuid DEFAULT public.uuid_generate_v1() NOT NULL,
    secret_id uuid,
    meta jsonb,
    is_disabled boolean DEFAULT false NOT NULL,
    is_triggers_disabled boolean DEFAULT false NOT NULL,
    CONSTRAINT repo_branch_commit_check CHECK (((repo_branch IS NOT NULL) OR (repo_commit_id IS NOT NULL)))
);


--
-- Name: COLUMN repositories.repo_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.repositories.repo_name IS 'Name (key) of a repository';


--
-- Name: COLUMN repositories.repo_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.repositories.repo_url IS 'URL of a repository';


--
-- Name: COLUMN repositories.repo_branch; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.repositories.repo_branch IS 'Name of a repository''s branch';


--
-- Name: COLUMN repositories.repo_commit_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.repositories.repo_commit_id IS 'Repository''s commit id';


--
-- Name: role_ldap_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_ldap_groups (
    role_id uuid NOT NULL,
    ldap_group character varying(1024) NOT NULL
);


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL
);


--
-- Name: TABLE role_permissions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.role_permissions IS 'Permissions of roles';


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    role_id uuid DEFAULT public.uuid_generate_v1() NOT NULL,
    role_name character varying(256) NOT NULL,
    global_reader boolean DEFAULT false,
    global_writer boolean DEFAULT false
);


--
-- Name: secret_team_access; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.secret_team_access (
    secret_id uuid NOT NULL,
    team_id uuid NOT NULL,
    access_level character varying(128) DEFAULT 'READER'::character varying NOT NULL
);


--
-- Name: secrets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.secrets (
    secret_name character varying(128) NOT NULL,
    secret_type character varying(32) NOT NULL,
    secret_data bytea,
    encrypted_by character varying(128) DEFAULT 'SERVER_KEY'::character varying NOT NULL,
    secret_id uuid DEFAULT public.uuid_generate_v1() NOT NULL,
    org_id uuid DEFAULT '0fac1b18-d179-11e7-b3e7-d7df4543ed4f'::uuid NOT NULL,
    owner_id uuid,
    visibility character varying(128) DEFAULT 'PUBLIC'::character varying NOT NULL,
    store_type character varying(128) DEFAULT 'CONCORD'::character varying,
    last_updated_at timestamp with time zone,
    secret_salt bytea NOT NULL,
    hash_algorithm character varying(32) DEFAULT 'md5'::character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: COLUMN secrets.secret_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.secrets.secret_name IS 'Name (key) of a secret';


--
-- Name: COLUMN secrets.secret_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.secrets.secret_type IS 'Type: SSH_KEY, HTTP_BASIC';


--
-- Name: task_locks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task_locks (
    lock_key character varying(64) NOT NULL,
    locked boolean DEFAULT false NOT NULL,
    locked_at timestamp with time zone,
    lock_counter integer DEFAULT 0 NOT NULL
);


--
-- Name: tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tasks (
    task_id character varying(64) NOT NULL,
    task_interval bigint NOT NULL,
    task_status public.task_status_type,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    last_updated_at timestamp with time zone,
    last_error_at timestamp with time zone,
    last_error text
);


--
-- Name: COLUMN tasks.task_interval; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tasks.task_interval IS 'Start interval (in seconds)';


--
-- Name: team_ldap_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.team_ldap_groups (
    team_id uuid NOT NULL,
    ldap_group character varying(1024) NOT NULL,
    team_role character varying(128) DEFAULT 'MEMBER'::character varying NOT NULL
);


--
-- Name: team_ui_process_cards; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.team_ui_process_cards (
    team_id uuid NOT NULL,
    ui_process_card_id uuid NOT NULL
);


--
-- Name: teams; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teams (
    team_id uuid DEFAULT public.uuid_generate_v1() NOT NULL,
    team_name character varying(128) NOT NULL,
    description character varying(2048),
    org_id uuid DEFAULT '0fac1b18-d179-11e7-b3e7-d7df4543ed4f'::uuid NOT NULL
);


--
-- Name: TABLE teams; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.teams IS 'User teams';


--
-- Name: template_aliases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.template_aliases (
    template_alias character varying(128) NOT NULL,
    template_url character varying(2048) NOT NULL
);


--
-- Name: trigger_schedule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trigger_schedule (
    trigger_id uuid NOT NULL,
    fire_at timestamp with time zone NOT NULL
);


--
-- Name: triggers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.triggers (
    trigger_id uuid DEFAULT public.uuid_generate_v1() NOT NULL,
    project_id uuid NOT NULL,
    repo_id uuid NOT NULL,
    event_source character varying(128) NOT NULL,
    arguments jsonb,
    conditions jsonb,
    active_profiles character varying[],
    trigger_cfg jsonb
);


--
-- Name: ui_process_cards; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ui_process_cards (
    ui_process_card_id uuid DEFAULT public.uuid_generate_v1() NOT NULL,
    project_id uuid NOT NULL,
    repo_id uuid,
    name character varying(128) NOT NULL,
    entry_point character varying(256),
    description character varying(512),
    icon bytea,
    form bytea,
    data jsonb,
    owner_id uuid,
    order_id integer
);


--
-- Name: user_ldap_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_ldap_groups (
    user_id uuid NOT NULL,
    ldap_group character varying(1024) NOT NULL
);


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    user_id uuid NOT NULL,
    role_id uuid DEFAULT public.uuid_generate_v1() NOT NULL
);


--
-- Name: user_teams; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_teams (
    user_id uuid NOT NULL,
    team_id uuid NOT NULL,
    team_role character varying(128) DEFAULT 'MEMBER'::character varying NOT NULL
);


--
-- Name: user_ui_process_cards; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_ui_process_cards (
    user_id uuid NOT NULL,
    ui_process_card_id uuid NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    user_id uuid DEFAULT public.uuid_generate_v1() NOT NULL,
    username character varying(64) NOT NULL,
    display_name character varying(1024),
    user_type character varying(32) DEFAULT 'LDAP'::character varying NOT NULL,
    user_email character varying(512),
    is_disabled boolean DEFAULT false NOT NULL,
    last_group_sync_dt timestamp with time zone,
    domain character varying(512),
    disabled_date timestamp with time zone,
    is_permanently_disabled boolean DEFAULT false NOT NULL
);


--
-- Name: TABLE users; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.users IS 'Users';


--
-- Name: COLUMN users.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.user_id IS 'Unique user ID';


--
-- Name: COLUMN users.username; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.username IS 'Unique name of a user (login)';


--
-- Name: COLUMN users.last_group_sync_dt; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.last_group_sync_dt IS 'Timestamp of the last user group synchronization attempt';


--
-- Name: v_audit_log; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.v_audit_log AS
 SELECT a.entry_seq,
    a.entry_date,
    a.user_id,
    ( SELECT u.username
           FROM public.users u
          WHERE (u.user_id = a.user_id)) AS username,
    a.entry_object,
    a.entry_action,
    a.entry_details
   FROM public.audit_log a;


--
-- Name: v_user_roles; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.v_user_roles AS
 SELECT user_roles.user_id,
    user_roles.role_id
   FROM public.user_roles
UNION
 SELECT DISTINCT ulg.user_id,
    rlg.role_id
   FROM public.role_ldap_groups rlg,
    public.user_ldap_groups ulg
  WHERE (((ulg.ldap_group)::text = (rlg.ldap_group)::text) AND (NOT (EXISTS ( SELECT 1
           FROM public.user_roles ur
          WHERE ((ur.user_id = ulg.user_id) AND (ur.role_id = rlg.role_id))))));


--
-- Name: v_user_teams; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.v_user_teams AS
 SELECT user_teams.user_id,
    user_teams.team_id,
    user_teams.team_role
   FROM public.user_teams
UNION
 SELECT DISTINCT ulg.user_id,
    tlg.team_id,
    tlg.team_role
   FROM public.team_ldap_groups tlg,
    public.user_ldap_groups ulg
  WHERE (((ulg.ldap_group)::text = (tlg.ldap_group)::text) AND (NOT (EXISTS ( SELECT 1
           FROM public.user_teams ut
          WHERE ((ut.user_id = ulg.user_id) AND (ut.team_id = tlg.team_id))))));


--
-- Name: agent_commands AGENT_COMMANDS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_commands
    ADD CONSTRAINT "AGENT_COMMANDS_pkey" PRIMARY KEY (command_id);


--
-- Name: api_keys API_KEYS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_keys
    ADD CONSTRAINT "API_KEYS_pkey" PRIMARY KEY (key_id);


--
-- Name: event_processor_marker EVENT_PROCESSOR_MARKER_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_processor_marker
    ADD CONSTRAINT "EVENT_PROCESSOR_MARKER_pkey" PRIMARY KEY (processor_name);


--
-- Name: external_app_users EXTERNAL_APP_USERS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.external_app_users
    ADD CONSTRAINT "EXTERNAL_APP_USERS_pkey" PRIMARY KEY (external_user_id);


--
-- Name: json_stores INVENTORIES_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.json_stores
    ADD CONSTRAINT "INVENTORIES_pkey" PRIMARY KEY (json_store_id);


--
-- Name: json_store_data INVENTORY_DATA_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.json_store_data
    ADD CONSTRAINT "INVENTORY_DATA_pkey" PRIMARY KEY (json_store_id, item_path);


--
-- Name: json_store_queries INVENTORY_QUERIES_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.json_store_queries
    ADD CONSTRAINT "INVENTORY_QUERIES_pkey" PRIMARY KEY (query_id);


--
-- Name: json_store_team_access INVENTORY_TEAM_ACCESS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.json_store_team_access
    ADD CONSTRAINT "INVENTORY_TEAM_ACCESS_pkey" PRIMARY KEY (json_store_id, team_id);


--
-- Name: organizations ORGANIZATIONS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT "ORGANIZATIONS_pkey" PRIMARY KEY (org_id);


--
-- Name: permissions PERMISSIONS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT "PERMISSIONS_pkey" PRIMARY KEY (permission_id);


--
-- Name: policies POLICIES_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policies
    ADD CONSTRAINT "POLICIES_pkey" PRIMARY KEY (policy_id);


--
-- Name: process_checkpoints PROCESS_CHECKPOINTS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_checkpoints
    ADD CONSTRAINT "PROCESS_CHECKPOINTS_pkey" PRIMARY KEY (checkpoint_id);


--
-- Name: process_initial_state PROCESS_INITIAL_STATE_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_initial_state
    ADD CONSTRAINT "PROCESS_INITIAL_STATE_pkey" PRIMARY KEY (instance_id, item_path);


--
-- Name: process_queue PROCESS_QUEUE_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_queue
    ADD CONSTRAINT "PROCESS_QUEUE_pkey" PRIMARY KEY (instance_id);


--
-- Name: project_team_access PROJECT_TEAM_ACCESS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_team_access
    ADD CONSTRAINT "PROJECT_TEAM_ACCESS_pkey" PRIMARY KEY (project_id, team_id);


--
-- Name: roles ROLES_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT "ROLES_pkey" PRIMARY KEY (role_id);


--
-- Name: role_permissions ROLE_PERMISSIONS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT "ROLE_PERMISSIONS_pkey" PRIMARY KEY (role_id, permission_id);


--
-- Name: secret_team_access SECRET_TEAM_ACCESS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.secret_team_access
    ADD CONSTRAINT "SECRET_TEAM_ACCESS_pkey" PRIMARY KEY (secret_id, team_id);


--
-- Name: tasks TASKS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tasks
    ADD CONSTRAINT "TASKS_pkey" PRIMARY KEY (task_id);


--
-- Name: task_locks TASK_LOCKS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_locks
    ADD CONSTRAINT "TASK_LOCKS_pkey" PRIMARY KEY (lock_key);


--
-- Name: teams TEAMS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT "TEAMS_pkey" PRIMARY KEY (team_id);


--
-- Name: template_aliases TEMPLATE_ALIASES_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.template_aliases
    ADD CONSTRAINT "TEMPLATE_ALIASES_pkey" PRIMARY KEY (template_alias);


--
-- Name: triggers TRIGGERS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.triggers
    ADD CONSTRAINT "TRIGGERS_pkey" PRIMARY KEY (trigger_id);


--
-- Name: ui_process_cards UI_PROCESS_CARDS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ui_process_cards
    ADD CONSTRAINT "UI_PROCESS_CARDS_pkey" PRIMARY KEY (ui_process_card_id);


--
-- Name: users USERS_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT "USERS_pkey" PRIMARY KEY (user_id);


--
-- Name: user_roles USER_ROLES_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT "USER_ROLES_pkey" PRIMARY KEY (user_id, role_id);


--
-- Name: api_keys api_keys_api_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_keys
    ADD CONSTRAINT api_keys_api_key_key UNIQUE (api_key);


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (entry_seq);


--
-- Name: json_stores inventories_org_id_inventory_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.json_stores
    ADD CONSTRAINT inventories_org_id_inventory_name_key UNIQUE (org_id, json_store_name);


--
-- Name: organizations organizations_org_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT organizations_org_name_key UNIQUE (org_name);


--
-- Name: project_kv_store pk_project_kv; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_kv_store
    ADD CONSTRAINT pk_project_kv PRIMARY KEY (project_id, value_key);


--
-- Name: projects pk_projects; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT pk_projects PRIMARY KEY (project_id);


--
-- Name: repositories pk_repositories; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repositories
    ADD CONSTRAINT pk_repositories PRIMARY KEY (repo_id);


--
-- Name: secrets pk_secrets; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.secrets
    ADD CONSTRAINT pk_secrets PRIMARY KEY (secret_id);


--
-- Name: policies policies_policy_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policies
    ADD CONSTRAINT policies_policy_name_key UNIQUE (policy_name);


--
-- Name: process_events process_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_events
    ADD CONSTRAINT process_events_pkey PRIMARY KEY (event_seq);


--
-- Name: process_meta process_meta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_meta
    ADD CONSTRAINT process_meta_pkey PRIMARY KEY (instance_id, instance_created_at);


--
-- Name: process_state process_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_state
    ADD CONSTRAINT process_state_pkey PRIMARY KEY (instance_id, instance_created_at, item_path);


--
-- Name: process_trigger_info process_trigger_info_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_trigger_info
    ADD CONSTRAINT process_trigger_info_pkey PRIMARY KEY (instance_id, instance_created_at);


--
-- Name: project_secrets project_secrets_secret_id_project_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_secrets
    ADD CONSTRAINT project_secrets_secret_id_project_id_key UNIQUE (secret_id, project_id);


--
-- Name: projects projects_org_id_project_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_org_id_project_name_key UNIQUE (org_id, project_name);


--
-- Name: repositories repositories_project_id_repo_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repositories
    ADD CONSTRAINT repositories_project_id_repo_name_key UNIQUE (project_id, repo_name);


--
-- Name: role_ldap_groups role_ldap_groups_role_id_ldap_group_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_ldap_groups
    ADD CONSTRAINT role_ldap_groups_role_id_ldap_group_key UNIQUE (role_id, ldap_group);


--
-- Name: roles roles_role_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_role_name_key UNIQUE (role_name);


--
-- Name: secrets secrets_org_id_secret_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.secrets
    ADD CONSTRAINT secrets_org_id_secret_name_key UNIQUE (org_id, secret_name);


--
-- Name: team_ldap_groups team_ldap_groups_team_id_ldap_group_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_ldap_groups
    ADD CONSTRAINT team_ldap_groups_team_id_ldap_group_key UNIQUE (team_id, ldap_group);


--
-- Name: team_ui_process_cards team_ui_process_cards_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_ui_process_cards
    ADD CONSTRAINT team_ui_process_cards_pkey PRIMARY KEY (team_id, ui_process_card_id);


--
-- Name: teams teams_org_id_team_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT teams_org_id_team_name_key UNIQUE (org_id, team_name);


--
-- Name: json_store_queries unq_inventory_queries; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.json_store_queries
    ADD CONSTRAINT unq_inventory_queries UNIQUE (json_store_id, query_name);


--
-- Name: user_teams user_teams_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_teams
    ADD CONSTRAINT user_teams_pkey PRIMARY KEY (user_id, team_id);


--
-- Name: user_ui_process_cards user_ui_process_cards_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_ui_process_cards
    ADD CONSTRAINT user_ui_process_cards_pkey PRIMARY KEY (user_id, ui_process_card_id);


--
-- Name: idx_a_cmd_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_a_cmd_status ON public.agent_commands USING btree (command_status);


--
-- Name: idx_api_key_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_api_key_user ON public.api_keys USING btree (user_id);


--
-- Name: idx_api_keys_name_user_not_null; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_api_keys_name_user_not_null ON public.api_keys USING btree (key_name, user_id) WHERE (user_id IS NOT NULL);


--
-- Name: idx_api_keys_name_user_null; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_api_keys_name_user_null ON public.api_keys USING btree (key_name) WHERE (user_id IS NULL);


--
-- Name: idx_audit_log_details; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_details ON public.audit_log USING gin (entry_details);


--
-- Name: idx_checkpoints_proc_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_checkpoints_proc_id ON public.process_checkpoints USING btree (instance_id);


--
-- Name: idx_pld_ids; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pld_ids ON public.process_log_data USING btree (instance_id, instance_created_at, segment_id);


--
-- Name: idx_pls_ids; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pls_ids ON public.process_log_segments USING btree (instance_id, instance_created_at);


--
-- Name: idx_policy_link_1; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_policy_link_1 ON public.policy_links USING btree (org_id, project_id, policy_id) WHERE ((org_id IS NOT NULL) AND (project_id IS NOT NULL));


--
-- Name: idx_policy_link_2; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_policy_link_2 ON public.policy_links USING btree (policy_id) WHERE ((org_id IS NULL) AND (project_id IS NULL));


--
-- Name: idx_policy_link_3; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_policy_link_3 ON public.policy_links USING btree (org_id, policy_id) WHERE ((org_id IS NOT NULL) AND (project_id IS NULL));


--
-- Name: idx_policy_link_4; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_policy_link_4 ON public.policy_links USING btree (project_id, policy_id) WHERE ((org_id IS NULL) AND (project_id IS NOT NULL));


--
-- Name: idx_policy_link_5; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_policy_link_5 ON public.policy_links USING btree (org_id, project_id, policy_id, user_id) WHERE ((org_id IS NOT NULL) AND (project_id IS NOT NULL) AND (user_id IS NOT NULL));


--
-- Name: idx_policy_link_6; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_policy_link_6 ON public.policy_links USING btree (user_id, policy_id) WHERE ((org_id IS NULL) AND (project_id IS NULL) AND (user_id IS NOT NULL));


--
-- Name: idx_proc_enqueued; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proc_enqueued ON public.process_queue USING btree (current_status) WHERE ((current_status)::text = 'ENQUEUED'::text);


--
-- Name: idx_proc_events; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proc_events ON public.process_events USING btree (instance_id, instance_created_at, event_date, event_type);


--
-- Name: idx_proc_events_folding; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proc_events_folding ON public.process_events USING btree (event_seq, event_type);


--
-- Name: idx_proc_initiator_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proc_initiator_id ON public.process_queue USING btree (initiator_id);


--
-- Name: idx_proc_meta_meta; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proc_meta_meta ON public.process_meta USING gin (meta jsonb_path_ops);


--
-- Name: idx_proc_q_c_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proc_q_c_status ON public.process_queue USING btree (current_status);


--
-- Name: idx_proc_q_cr_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proc_q_cr_at ON public.process_queue USING btree (created_at);


--
-- Name: idx_proc_q_par_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proc_q_par_id ON public.process_queue USING btree (parent_instance_id);


--
-- Name: idx_proc_q_prj_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proc_q_prj_id ON public.process_queue USING btree (project_id);


--
-- Name: idx_process_locks_1; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_process_locks_1 ON public.process_locks USING btree (org_id, lock_name) WHERE (lock_scope = 'ORG'::public.process_lock_scope);


--
-- Name: idx_process_locks_2; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_process_locks_2 ON public.process_locks USING btree (project_id, lock_name) WHERE (lock_scope = 'PROJECT'::public.process_lock_scope);


--
-- Name: idx_process_wait_cond_poll; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_process_wait_cond_poll ON public.process_wait_conditions USING btree (instance_created_at, id_seq) WHERE (is_waiting = true);


--
-- Name: idx_process_wait_conditions_ids; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_process_wait_conditions_ids ON public.process_wait_conditions USING btree (instance_id, instance_created_at);


--
-- Name: idx_trig_ev_src; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trig_ev_src ON public.triggers USING btree (event_source);


--
-- Name: idx_trigger_pr_id_repo_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trigger_pr_id_repo_id ON public.triggers USING btree (project_id, repo_id);


--
-- Name: idx_trigger_sched_fire_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trigger_sched_fire_date ON public.trigger_schedule USING btree (fire_at DESC);


--
-- Name: idx_user_ldap_groups_groups; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_ldap_groups_groups ON public.user_ldap_groups USING btree (ldap_group);


--
-- Name: idx_users_uniq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_users_uniq ON public.users USING btree (lower((username)::text), lower((domain)::text), user_type);


--
-- Name: idx_users_username; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_username ON public.users USING gin (username public.gin_trgm_ops);


--
-- Name: idx_wait_conditions; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wait_conditions ON public.process_wait_conditions USING gin (wait_conditions);


--
-- Name: project_secrets_project_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX project_secrets_project_idx ON public.project_secrets USING btree (project_id);


--
-- Name: project_secrets_secret_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX project_secrets_secret_idx ON public.project_secrets USING btree (secret_id);


--
-- Name: admins fk_admins_u_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admins
    ADD CONSTRAINT fk_admins_u_id FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: api_keys fk_api_key_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_keys
    ADD CONSTRAINT fk_api_key_user FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: external_app_users fk_external_app_users_user_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.external_app_users
    ADD CONSTRAINT fk_external_app_users_user_id FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: json_stores fk_inv_org_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.json_stores
    ADD CONSTRAINT fk_inv_org_id FOREIGN KEY (org_id) REFERENCES public.organizations(org_id) ON DELETE CASCADE;


--
-- Name: json_store_team_access fk_inv_t_a_inv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.json_store_team_access
    ADD CONSTRAINT fk_inv_t_a_inv FOREIGN KEY (json_store_id) REFERENCES public.json_stores(json_store_id) ON DELETE CASCADE;


--
-- Name: json_store_team_access fk_inv_t_a_t; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.json_store_team_access
    ADD CONSTRAINT fk_inv_t_a_t FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE CASCADE;


--
-- Name: json_stores fk_inventories_own_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.json_stores
    ADD CONSTRAINT fk_inventories_own_id FOREIGN KEY (owner_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: json_stores fk_inventories_parent; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.json_stores
    ADD CONSTRAINT fk_inventories_parent FOREIGN KEY (parent_inventory_id) REFERENCES public.json_stores(json_store_id) ON DELETE CASCADE;


--
-- Name: json_store_data fk_inventory_data_inventory; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.json_store_data
    ADD CONSTRAINT fk_inventory_data_inventory FOREIGN KEY (json_store_id) REFERENCES public.json_stores(json_store_id) ON DELETE CASCADE;


--
-- Name: json_store_queries fk_inventory_queries_inventory; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.json_store_queries
    ADD CONSTRAINT fk_inventory_queries_inventory FOREIGN KEY (json_store_id) REFERENCES public.json_stores(json_store_id) ON DELETE CASCADE;


--
-- Name: organizations fk_org_owner_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT fk_org_owner_id FOREIGN KEY (owner_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: policies fk_policies_parent; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policies
    ADD CONSTRAINT fk_policies_parent FOREIGN KEY (parent_policy_id) REFERENCES public.policies(policy_id) ON DELETE SET NULL;


--
-- Name: policy_links fk_policy_link_policy_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policy_links
    ADD CONSTRAINT fk_policy_link_policy_id FOREIGN KEY (policy_id) REFERENCES public.policies(policy_id) ON DELETE CASCADE;


--
-- Name: policy_links fk_policy_link_project_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policy_links
    ADD CONSTRAINT fk_policy_link_project_id FOREIGN KEY (project_id) REFERENCES public.projects(project_id) ON DELETE CASCADE;


--
-- Name: policy_links fk_policy_link_repo_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policy_links
    ADD CONSTRAINT fk_policy_link_repo_id FOREIGN KEY (org_id) REFERENCES public.organizations(org_id) ON DELETE CASCADE;


--
-- Name: policy_links fk_policy_links_user_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policy_links
    ADD CONSTRAINT fk_policy_links_user_id FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: process_queue fk_pq_initiator_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_queue
    ADD CONSTRAINT fk_pq_initiator_id FOREIGN KEY (initiator_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: process_queue fk_pq_prj_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_queue
    ADD CONSTRAINT fk_pq_prj_id FOREIGN KEY (project_id) REFERENCES public.projects(project_id) ON DELETE SET NULL;


--
-- Name: process_queue fk_pq_repo_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_queue
    ADD CONSTRAINT fk_pq_repo_id FOREIGN KEY (repo_id) REFERENCES public.repositories(repo_id) ON DELETE SET NULL;


--
-- Name: projects fk_prj_org_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT fk_prj_org_id FOREIGN KEY (org_id) REFERENCES public.organizations(org_id) ON DELETE CASCADE;


--
-- Name: projects fk_prj_own_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT fk_prj_own_id FOREIGN KEY (owner_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: project_team_access fk_prj_t_a_prj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_team_access
    ADD CONSTRAINT fk_prj_t_a_prj FOREIGN KEY (project_id) REFERENCES public.projects(project_id) ON DELETE CASCADE;


--
-- Name: project_team_access fk_prj_t_a_t; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_team_access
    ADD CONSTRAINT fk_prj_t_a_t FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE CASCADE;


--
-- Name: process_locks fk_process_locks_instance_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_locks
    ADD CONSTRAINT fk_process_locks_instance_id FOREIGN KEY (instance_id) REFERENCES public.process_queue(instance_id) ON DELETE CASCADE;


--
-- Name: process_locks fk_process_locks_org_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_locks
    ADD CONSTRAINT fk_process_locks_org_id FOREIGN KEY (org_id) REFERENCES public.organizations(org_id) ON DELETE CASCADE;


--
-- Name: process_locks fk_process_locks_project_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_locks
    ADD CONSTRAINT fk_process_locks_project_id FOREIGN KEY (project_id) REFERENCES public.projects(project_id) ON DELETE CASCADE;


--
-- Name: project_secrets fk_project_secrets_project; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_secrets
    ADD CONSTRAINT fk_project_secrets_project FOREIGN KEY (project_id) REFERENCES public.projects(project_id) ON DELETE CASCADE;


--
-- Name: project_secrets fk_project_secrets_secret; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_secrets
    ADD CONSTRAINT fk_project_secrets_secret FOREIGN KEY (secret_id) REFERENCES public.secrets(secret_id) ON DELETE CASCADE;


--
-- Name: repositories fk_repo_prj_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repositories
    ADD CONSTRAINT fk_repo_prj_id FOREIGN KEY (project_id) REFERENCES public.projects(project_id) ON DELETE CASCADE;


--
-- Name: role_permissions fk_role_permissions; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fk_role_permissions FOREIGN KEY (permission_id) REFERENCES public.permissions(permission_id) ON DELETE CASCADE;


--
-- Name: role_permissions fk_role_permissions_roles; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fk_role_permissions_roles FOREIGN KEY (role_id) REFERENCES public.roles(role_id) ON DELETE CASCADE;


--
-- Name: repositories fk_rp_scr_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repositories
    ADD CONSTRAINT fk_rp_scr_id FOREIGN KEY (secret_id) REFERENCES public.secrets(secret_id) ON DELETE SET NULL;


--
-- Name: secrets fk_scrt_own_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.secrets
    ADD CONSTRAINT fk_scrt_own_id FOREIGN KEY (owner_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: secret_team_access fk_scrt_t_a_scrt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.secret_team_access
    ADD CONSTRAINT fk_scrt_t_a_scrt FOREIGN KEY (secret_id) REFERENCES public.secrets(secret_id) ON DELETE CASCADE;


--
-- Name: secret_team_access fk_scrt_t_a_t; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.secret_team_access
    ADD CONSTRAINT fk_scrt_t_a_t FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE CASCADE;


--
-- Name: secrets fk_secret_org_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.secrets
    ADD CONSTRAINT fk_secret_org_id FOREIGN KEY (org_id) REFERENCES public.organizations(org_id) ON DELETE CASCADE;


--
-- Name: team_ui_process_cards fk_team_ui_p_cards_t_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_ui_process_cards
    ADD CONSTRAINT fk_team_ui_p_cards_t_id FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE CASCADE;


--
-- Name: user_ui_process_cards fk_team_ui_process_cards_ui_p_c_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_ui_process_cards
    ADD CONSTRAINT fk_team_ui_process_cards_ui_p_c_id FOREIGN KEY (ui_process_card_id) REFERENCES public.ui_process_cards(ui_process_card_id) ON DELETE CASCADE;


--
-- Name: trigger_schedule fk_trigger_schedule_tr_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trigger_schedule
    ADD CONSTRAINT fk_trigger_schedule_tr_id FOREIGN KEY (trigger_id) REFERENCES public.triggers(trigger_id) ON DELETE CASCADE;


--
-- Name: triggers fk_triggers_project_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.triggers
    ADD CONSTRAINT fk_triggers_project_id FOREIGN KEY (project_id) REFERENCES public.projects(project_id) ON DELETE CASCADE;


--
-- Name: triggers fk_triggers_repo_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.triggers
    ADD CONSTRAINT fk_triggers_repo_id FOREIGN KEY (repo_id) REFERENCES public.repositories(repo_id) ON DELETE CASCADE;


--
-- Name: user_roles fk_u_r_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fk_u_r_role FOREIGN KEY (role_id) REFERENCES public.roles(role_id) ON DELETE CASCADE;


--
-- Name: user_roles fk_u_r_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fk_u_r_user FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: ui_process_cards fk_ui_process_cards_owner_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ui_process_cards
    ADD CONSTRAINT fk_ui_process_cards_owner_id FOREIGN KEY (owner_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: ui_process_cards fk_ui_process_cards_p_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ui_process_cards
    ADD CONSTRAINT fk_ui_process_cards_p_id FOREIGN KEY (project_id) REFERENCES public.projects(project_id) ON DELETE CASCADE;


--
-- Name: ui_process_cards fk_ui_process_cards_r_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ui_process_cards
    ADD CONSTRAINT fk_ui_process_cards_r_id FOREIGN KEY (repo_id) REFERENCES public.repositories(repo_id) ON DELETE CASCADE;


--
-- Name: user_ldap_groups fk_user_ldap_groups_user_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_ldap_groups
    ADD CONSTRAINT fk_user_ldap_groups_user_id FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: user_ui_process_cards fk_user_ui_process_cards_u_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_ui_process_cards
    ADD CONSTRAINT fk_user_ui_process_cards_u_id FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: user_ui_process_cards fk_user_ui_process_cards_ui_p_c_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_ui_process_cards
    ADD CONSTRAINT fk_user_ui_process_cards_ui_p_c_id FOREIGN KEY (ui_process_card_id) REFERENCES public.ui_process_cards(ui_process_card_id) ON DELETE CASCADE;


--
-- Name: user_teams fk_usr_teams_team; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_teams
    ADD CONSTRAINT fk_usr_teams_team FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE CASCADE;


--
-- Name: user_teams fk_usr_teams_usr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_teams
    ADD CONSTRAINT fk_usr_teams_usr FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--
