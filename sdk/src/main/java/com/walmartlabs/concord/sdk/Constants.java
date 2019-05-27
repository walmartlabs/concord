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
    }

    /**
     * Request data keys.
     */
    public static class Request {

        /**
         * Key of a process arguments object in a request data JSON.
         */
        public static final String ARGUMENTS_KEY = "arguments";

        /**
         * Key of a process dependencies list in a request data JSON.
         */
        public static final String DEPENDENCIES_KEY = "dependencies";

        /**
         * Process entry point.
         */
        public static final String ENTRY_POINT_KEY = "entryPoint";

        /**
         * Name of the default entry point.
         */
        public static final String DEFAULT_ENTRY_POINT_NAME = "default";

        /**
         * Active profiles.
         */
        public static final String ACTIVE_PROFILES_KEY = "activeProfiles";

        /**
         * Template name.
         */
        @Deprecated
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
         * Requirements.
         */
        public static final String REQUIREMENTS = "requirements";

        /**
         * Process metadata.
         */
        public static final String META = "meta";

        /**
         * Container configuration.
         */
        public static final String CONTAINER = "container";

        /**
         * Process timeout.
         */
        public static final String PROCESS_TIMEOUT = "processTimeout";

        /**
         * A specific GIT commit ID to use.
         */
        public static final String REPO_COMMIT_ID = "repoCommitId";

        /**
         * A specific branch name or a tag to use.
         */
        public static final String REPO_BRANCH_OR_TAG = "repoBranchOrTag";

        /**
         * Exclusive process.
         */
        public static final String EXCLUSIVE_EXEC = "exclusiveExec";
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
         */
        public static final String REQUEST_DATA_FILE_NAME = "_main.json";

        /**
         * Directory which contains job "attachments": reports, stats, etc.
         */
        public static final String JOB_ATTACHMENTS_DIR_NAME = "_attachments";

        /**
         * Directory which contains job "checkpoints": reports, stats, etc.
         */
        public static final String JOB_CHECKPOINTS_DIR_NAME = "_checkpoints";

        /**
         * Directory which contains process' state.
         */
        public static final String JOB_STATE_DIR_NAME = "_state";

        /**
         * Directory which contains process' forms.
         */
        public static final String JOB_FORMS_DIR_NAME = "forms";

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
         */
        public static final String SESSION_TOKEN_FILE_NAME = ".session_token";

        /**
         * File which contains custom error messages for forms.
         */
        public static final String ERROR_MESSAGES_FILE_NAME = "locale.properties";

        /**
         * File which contains checkpoint metadata.
         */
        public static final String CHECKPOINT_META_FILE_NAME = ".checkpoint";
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

    /**
     * Typical part names in multipart/form-data requests, e.g. the start process request.
     */
    public static class Multipart {

        public static final String PROJECT_ID = "projectId";

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
}
