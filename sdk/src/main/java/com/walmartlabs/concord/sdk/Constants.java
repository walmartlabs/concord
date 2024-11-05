package com.walmartlabs.concord.sdk;

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


/**
 * Project and process constants.
 */
public class Constants {

    /**
     * Process context variables.
     */
    public static class Context {

        /**
         * Execution context.
         */
        public static final String CONTEXT_KEY = "context";

        /**
         * Path (a string) to a local directory which contains agent's payload.
         */
        public static final String WORK_DIR_KEY = "workDir";

        /**
         * ID of the current process instance.
         */
        public static final String TX_ID_KEY = "txId";

        /**
         * List of OUT variables of a process.
         */
        public static final String OUT_EXPRESSIONS_KEY = "_out";

        /**
         * Stores the last caught exception.
         */
        public static final String LAST_ERROR_KEY = "lastError";

        /**
         * Execution context.
         *
         * @deprecated use {@link Constants.Context#CONTEXT_KEY}
         */
        @Deprecated
        public static final String EXECUTION_CONTEXT_KEY = "execution";

        /**
         * Correlation ID of process events. Can be used to create "pre-" and "post-action" event records.
         */
        public static final String EVENT_CORRELATION_KEY = "__eventCorrelationId";

        /**
         * "pre-" event creation time. Can be used to calculate event durations.
         */
        public static final String EVENT_CREATED_AT_KEY = "__eventCreatedAt";

        /**
         * The maximum number of retries of the current `retry` block.
         */
        public static final String RETRY_COUNTER = "__retryCount";

        /**
         * The current number of retries of the current `retry` block.
         */
        public static final String CURRENT_RETRY_COUNTER = "__currentRetryCount";
    }

    /**
     * Request data keys.
     */
    public static class Request {

        public static final String CONFIGURATION_KEY = "configuration";

        /**
         * Runtime to use.
         */
        public static final String RUNTIME_KEY = "runtime";

        /**
         * Key of a process arguments object in a request data JSON.
         */
        public static final String ARGUMENTS_KEY = "arguments";

        /**
         * Key of a process dependencies list in a request data JSON.
         */
        public static final String DEPENDENCIES_KEY = "dependencies";

        /**
         * Key of a process dependencies list in a request data JSON.
         */
        public static final String EXTRA_DEPENDENCIES_KEY = "extraDependencies";

        /**
         * Process entry point.
         */
        public static final String ENTRY_POINT_KEY = "entryPoint";

        /**
         * Name of the default entry point.
         */
        public static final String DEFAULT_ENTRY_POINT_NAME = "default";

        /**
         * Run process in dry-run mode?.
         */
        public static final String DRY_RUN_MODE_KEY = "dryRun";

        /**
         * Active profiles.
         */
        public static final String ACTIVE_PROFILES_KEY = "activeProfiles";

        /**
         * Template name.
         */
        public static final String TEMPLATE_KEY = "template";

        /**
         * Process initiator's info.
         */
        public static final String INITIATOR_KEY = "initiator";

        /**
         * Process current user's info.
         */
        public static final String CURRENT_USER_KEY = "currentUser";

        /**
         * User request's metadata.
         */
        public static final String REQUEST_INFO_KEY = "requestInfo";

        /**
         * Project's metadata
         */
        public static final String PROJECT_INFO_KEY = "projectInfo";

        /**
         * Process's metadata
         */
        public static final String PROCESS_INFO_KEY = "processInfo";

        /**
         * Process tags.
         */
        public static final String TAGS_KEY = "tags";

        /**
         * ID of a parent process instance.
         */
        public static final String PARENT_INSTANCE_ID_KEY = "parentInstanceId";

        /**
         * If {@code true}, then `onCancel` flow will be ignored.
         */
        public static final String DISABLE_ON_CANCEL_KEY = "disableOnCancel";

        /**
         * If {@code true}, then `onFailure` flow will be ignored.
         */
        public static final String DISABLE_ON_FAILURE_KEY = "disableOnFailure";

        /**
         * If {@code true}, then `onTimeout` flow will be ignored.
         */
        public static final String DISABLE_ON_TIMEOUT_KEY = "disableOnTimeout";

        /**
         * Declares a list of OUT variables or expressions.
         */
        public static final String OUT_EXPRESSIONS_KEY = "out";

        /**
         * Enables additional debug logging for various states of execution.
         */
        public static final String DEBUG_KEY = "debug";

        /**
         * Schedules a process on the specified date and time.
         */
        public static final String START_AT_KEY = "startAt";

        /**
         * Process requirements (e.g. agent flavor, JVM args, etc).
         */
        public static final String REQUIREMENTS = "requirements";

        /**
         * Process metadata.
         */
        public static final String META = "meta";

        /**
         * Container configuration.
         * @deprecated the container-per-process execution mode is deprecated.
         */
        @Deprecated
        public static final String CONTAINER = "container";

        /**
         * Process timeout.
         */
        public static final String PROCESS_TIMEOUT = "processTimeout";

        /**
         * Handler process timeout.
         */
        public static final String HANDLER_PROCESS_TIMEOUT = "handlerProcessTimeout";

        /**
         * Timeout for process in Suspended state.
         */
        public static final String SUSPEND_TIMEOUT = "suspendTimeout";

        /**
         * A specific GIT commit ID to use.
         */
        public static final String REPO_COMMIT_ID = "repoCommitId";

        /**
         * A specific branch name or a tag to use.
         */
        public static final String REPO_BRANCH_OR_TAG = "repoBranchOrTag";

        /**
         * Exclusive params.
         */
        public static final String EXCLUSIVE = "exclusive";

        /**
         * Resume event name. Filled in for processes resumed after being suspended.
         * @deprecated see {@link #RESUME_EVENTS_KEY}
         */
        @Deprecated
        public static final String EVENT_NAME_KEY = "resumeEventName";

        /**
         * Resume events. Filled in for processes resumed after being suspended.
         */
        public static final String RESUME_EVENTS_KEY = "resumeEvents";

        /**
         * The runner's configuration section.
         */
        public static final String RUNNER_KEY = "runner";

        /**
         * The process' session token that can be used to talk to the API.
         */
        public static final String SESSION_TOKEN_KEY = "sessionToken";
    }

    public static class Trigger {

        /**
         * Cron expression.
         */
        public static final String CRON_SPEC = "spec";

        /**
         * Cron target time zone.
         */
        public static final String CRON_TIMEZONE = "timezone";

        /**
         * The time a cron event was scheduled for.
         */
        public static final String CRON_EVENT_FIREAT = "fireAt";

        /**
         * Whether to use the trigger's initiator as the process' initiator.
         */
        public static final String USE_INITIATOR = "useInitiator";

        /**
         * Use the trigger's commit id to start the process.
         */
        public static final String USE_EVENT_COMMIT_ID = "useEventCommitId";

        /**
         * Ignore empty {@code push} notifications
         * (events with "after" and "before" pointing to the same commit ID).
         */
        public static final String IGNORE_EMPTY_PUSH = "ignoreEmptyPush";

        /**
         * Used to match on the registered repositories in GitHub triggers.
         */
        public static final String REPOSITORY_INFO = "repositoryInfo";

        /**
         * {@code conditions} field. Used to match the specified parameters
         * with the incoming event's payload.
         */
        public static final String CONDITIONS = "conditions";

        /**
         * {@code version} field. Used to specify the trigger's syntax version.
         */
        public static final String VERSION = "version";
    }

    /**
     * Process metadata keys.
     */
    public static class Meta {

        /**
         * Internal metadata group.
         */
        public static final String SYSTEM_GROUP = "_system";

        /**
         * ID of the request which created the process.
         */
        public static final String REQUEST_ID = "requestId";
    }

    /**
     * Project files and directories.
     */
    public static class Files {

        /**
         * Files that the runtime considers "root" project files.
         */
        public static final String[] PROJECT_ROOT_FILE_NAMES = {
                ".concord.yml",
                "concord.yml",
                ".concord.yaml",
                "concord.yaml"
        };

        /**
         * Directory which contains payload data.
         */
        @Deprecated
        public static final String PAYLOAD_DIR_NAME = "payload";

        /**
         * File which contains the ID of a process.
         */
        public static final String INSTANCE_ID_FILE_NAME = "_instanceId";

        /**
         * Directory containing libraries (dependencies) of a payload.
         */
        public static final String LIBRARIES_DIR_NAME = "lib";

        /**
         * Directories which contain process definitions of a payload.
         */
        public static final String[] DEFINITIONS_DIR_NAMES = {"flows", "processes"}; // NOSONAR

        /**
         * Directory with Concord project definitions.
         */
        public static final String PROJECT_FILES_DIR_NAME = "concord";

        /**
         * Directory containing process profiles.
         */
        public static final String PROFILES_DIR_NAME = "profiles";

        /**
         * File which contains request data of a payload: process arguments, entry point name, etc.
         * @deprecated see {@link #CONFIGURATION_FILE_NAME}
         */
        @Deprecated
        public static final String REQUEST_DATA_FILE_NAME = "_main.json";

        /**
         * File which contains process configuration: process arguments, entry point name, etc.
         */
        public static final String CONFIGURATION_FILE_NAME = "_main.json";

        /**
         * Directory which contains job "attachments": reports, stats, etc.
         */
        public static final String JOB_ATTACHMENTS_DIR_NAME = "_attachments";

        /**
         * Directory which contains job "checkpoints": reports, stats, etc.
         */
        public static final String JOB_CHECKPOINTS_DIR_NAME = "_checkpoints";

        /**
         * Directory which contains job "session files":
         *  files that can be downloaded only with session key.
         */
        public static final String JOB_SESSION_FILES_DIR_NAME = "_session_files";

        /**
         * Directory which contains process' state.
         */
        public static final String JOB_STATE_DIR_NAME = "_state";

        /**
         * Directory which contains process' forms.
         */
        public static final String JOB_FORMS_DIR_NAME = "forms";

        /**
         * Directory which contains process' V2 forms.
         */
        public static final String JOB_FORMS_V2_DIR_NAME = "V2forms";

        /**
         * Directory which contains submitted form files.
         */
        public static final String FORM_FILES = "_form_files";

        /**
         * File which contains data of process' OUT variables.
         */
        public static final String OUT_VALUES_FILE_NAME = "out.json";

        /**
         * Marker file, indicating that a process was suspended.
         * It contains the list of waiting events.
         */
        public static final String SUSPEND_MARKER_FILE_NAME = "_suspend";

        /**
         * Marker file, indicating that a process should be resumed.
         * It contains the name of a resuming event.
         */
        public static final String RESUME_MARKER_FILE_NAME = "_resume";

        /**
         * Snapshot of the process' variables, taken each time the process stops.
         */
        public static final String LAST_KNOWN_VARIABLES_FILE_NAME = "_lastVariables";

        /**
         * The last unhandled error of the process, serialized to a file.
         */
        public static final String LAST_ERROR_FILE_NAME = "_lastError";

        /**
         * Concord system files.
         */
        public static final String CONCORD_SYSTEM_DIR_NAME = ".concord";

        /**
         * Concord TMP files.
         */
        public static final String CONCORD_TMP_DIR_NAME = ".concord_tmp";

        /**
         * File which contains process session token.
         * @deprecated use {@code ProcessConfiguration#sessionToken()}
         */
        @Deprecated
        public static final String SESSION_TOKEN_FILE_NAME = ".session_token";

        /**
         * File which contains custom error messages for forms.
         */
        public static final String ERROR_MESSAGES_FILE_NAME = "locale.properties";

        /**
         * File which contains checkpoint metadata.
         */
        public static final String CHECKPOINT_META_FILE_NAME = ".checkpoint";

        /**
         * Policy file.
         */
        public static final String POLICY_FILE_NAME = "policy.json";

        /**
         * Properties file with a list of default dependency versions.
         * @deprecated replaced with the "dependencyVersions" policy.
         */
        @Deprecated
        public static final String DEPENDENCY_VERSIONS_FILE_NAME = "dependencyversions.properties";

        /**
         * File which contains sensitive data of process.
         */
        public static final String SENSITIVE_DATA_FILE_NAME = "sensitive_data.json";
    }

    public static class Flows {

        /**
         * Failure-handling flow.
         */
        public static final String ON_FAILURE_FLOW = "onFailure";

        /**
         * Cancel-handling flow.RUN_AS
         */
        public static final String ON_CANCEL_FLOW = "onCancel";

        /**
         * Timeout-handling flow.
         */
        public static final String ON_TIMEOUT_FLOW = "onTimeout";
    }

    public static class Forms {

        /**
         * The form wizard will stop on the form with {@code yield=true}.
         */
        public static final String YIELD_KEY = "yield";

        public static final String FIELDS_KEY = "fields";

        /**
         * Additional values provided for the form.
         */
        public static final String VALUES_KEY = "values";

        /**
         * User qualifiers of forms.
         */
        public static final String RUN_AS_KEY = "runAs";

        public static final String RUN_AS_USERNAME_KEY = "username";

        public static final String RUN_AS_LDAP_KEY = "ldap";

        public static final String RUN_AS_GROUP_KEY = "group";

        public static final String RUN_AS_KEEP_KEY = "keep";

        /**
         * Form data field containing the submitter's user data.
         */
        public static final String SUBMITTED_BY_KEY = "submittedBy";

        /**
         * If {@code true} then the submitter's data will be stored in the {@link Forms#SUBMITTED_BY_KEY} field.
         */
        public static final String SAVE_SUBMITTED_BY_KEY = "saveSubmittedBy";
    }

    /**
     * Typical part names in multipart/form-data requests, e.g. the start process request.
     */
    public static class Multipart {

        public static final String PROJECT_ID = "projectId";

        // Contains list of project ids
        public static final String PROJECT_IDS = "projectIds";

        public static final String PROJECT_NAMES = "projects";

        public static final String PROJECT_NAME = "project";

        public static final String STORE_TYPE = "storeType";

        public static final String PUBLIC = "public";

        public static final String PRIVATE = "private";

        public static final String DATA = "data";

        public static final String NAME = "name";

        public static final String USERNAME = "username";

        public static final String PASSWORD = "password"; // NOSONAR

        public static final String TYPE = "type";

        public static final String GENERATE_PASSWORD = "generatePassword"; // NOSONAR

        public static final String STORE_PASSWORD = "storePassword"; // NOSONAR

        public static final String NEW_STORE_PASSWORD = "newStorePassword";

        public static final String VISIBILITY = "visibility";

        public static final String PARENT_INSTANCE_ID = "parentInstanceId";

        public static final String ENTRY_POINT = "entryPoint";

        public static final String ORG_ID = "orgId";

        public static final String ORG_NAME = "org";

        public static final String OUT_EXPR = "out";

        public static final String REPO_ID = "repoId";

        public static final String REPO_NAME = "repo";

        public static final String SYNC = "sync";

        public static final String META = "meta";
    }

    public static class Headers {

        public static final String SESSION_TOKEN = "X-Concord-SessionToken";

        public static final String SECRET_TYPE = "X-Concord-SecretType";

        public static final String ENABLE_HTTP_SESSION = "X-Concord-EnableSession";
    }

    /**
     * Agent parameters.
     */
    public static class Agent {

        /**
         * File which contains runtime parameters for agents: heap size, JVM arguments, etc.
         */
        public static final String AGENT_PARAMS_FILE_NAME = "_agent.json";

        /**
         * JVM parameters for an agent's job.
         */
        public static final String JVM_ARGS_KEY = "jvmArgs";
    }
}
