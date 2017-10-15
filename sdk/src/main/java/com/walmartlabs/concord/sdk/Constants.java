package com.walmartlabs.concord.sdk;

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
        public static final String TEMPLATE_KEY = "template";

        /**
         * Process initiator's info.
         */
        public static final String INITIATOR_KEY = "initiator";

        /**
         * User request's metadata.
         */
        public static final String REQUEST_INFO_KEY = "requestInfo";

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
         * Declares a list of OUT variables or expressions.
         */
        public static final String OUT_EXPRESSIONS_KEY = "out";
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
         * (Default) directory which contains process definitions of a payload.
         */
        public static final String DEFINITIONS_DIR_NAME = "flows";

        /**
         * Directories which contain process definitions of a payload.
         */
        public static final String[] DEFINITIONS_DIR_NAMES = {"flows", "processes"};

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
         * Directory which contains process' state.
         */
        public static final String JOB_STATE_DIR_NAME = "_state";

        /**
         * Directory which contains process' forms.
         */
        public static final String JOB_FORMS_DIR_NAME = "forms";
    }

    public static class Flows {

        /**
         * Failure-handling flow.
         */
        public static final String ON_FAILURE_FLOW = "onFailure";

        /**
         * Cancel-handling flow.
         */
        public static final String ON_CANCEL_FLOW = "onCancel";
    }
}
