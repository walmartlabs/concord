package com.walmartlabs.concord.project;

import io.takari.bpm.model.ProcessDefinition;

/**
 * Project and process constants.
 */
@Deprecated
public final class Constants {

    /**
     * Process context variables.
     */
    public static final class Context {

        /**
         * Execution context. Same as {@link #EXECUTION_CONTEXT_KEY}.
         */
        public static final String CONTEXT_KEY = com.walmartlabs.concord.sdk.Constants.Context.CONTEXT_KEY;

        /**
         * Execution context.
         * @deprecated Use {@link #CONTEXT_KEY}
         */
        @Deprecated
        public static final String EXECUTION_CONTEXT_KEY = "execution";

        /**
         * Process definition attribute: path to a local directory, containing agent's payload
         *
         * @see #WORK_DIR_KEY
         */
        public static final String LOCAL_PATH_ATTR = "localPath";

        /**
         * Execution context variable: path to a local directory, containing agent's payload.
         *
         * @see #WORK_DIR_KEY
         */
        public static final String LOCAL_PATH_KEY = ProcessDefinition.ATTRIBUTE_KEY_PREFIX + LOCAL_PATH_ATTR;

        /**
         * Path (a string) to a local directory, containing agent's payload.
         */
        public static final String WORK_DIR_KEY = "workDir";

        /**
         * ID of the current process instance.
         */
        public static final String TX_ID_KEY = com.walmartlabs.concord.sdk.Constants.Context.TX_ID_KEY;

        private Context() {
        }
    }

    /**
     * Request data keys.
     */
    public static final class Request {

        /**
         * Key of a process arguments object in a request data JSON.
         */
        public static final String ARGUMENTS_KEY = com.walmartlabs.concord.sdk.Constants.Request.ARGUMENTS_KEY;

        /**
         * Key of a process dependencies list in a request data JSON.
         */
        public static final String DEPENDENCIES_KEY = com.walmartlabs.concord.sdk.Constants.Request.DEPENDENCIES_KEY;

        /**
         * Process entry point.
         */
        public static final String ENTRY_POINT_KEY = com.walmartlabs.concord.sdk.Constants.Request.ENTRY_POINT_KEY;

        /**
         * Active profiles.
         */
        public static final String ACTIVE_PROFILES_KEY = com.walmartlabs.concord.sdk.Constants.Request.ACTIVE_PROFILES_KEY;

        /**
         * Template name.
         */
        public static final String TEMPLATE_KEY = com.walmartlabs.concord.sdk.Constants.Request.TEMPLATE_KEY;

        /**
         * Process initiator's info.
         */
        public static final String INITIATOR_KEY = com.walmartlabs.concord.sdk.Constants.Request.INITIATOR_KEY;

        /**
         * User request's metadata.
         */
        public static final String REQUEST_INFO_KEY = com.walmartlabs.concord.sdk.Constants.Request.REQUEST_INFO_KEY;

        private Request() {
        }
    }

    /**
     * Project files and directories.
     */
    public static final class Files {

        public static final String PAYLOAD_DIR_NAME = com.walmartlabs.concord.sdk.Constants.Files.PAYLOAD_DIR_NAME;

        /**
         * Directory containing libraries (dependencies) of a payload.
         */
        public static final String LIBRARIES_DIR_NAME = com.walmartlabs.concord.sdk.Constants.Files.LIBRARIES_DIR_NAME;

        /**
         * (Default) directory containing process definitions of a payload.
         */
        public static final String DEFINITIONS_DIR_NAME = com.walmartlabs.concord.sdk.Constants.Files.DEFINITIONS_DIR_NAME;

        /**
         * Directories containing process definitions of a payload.
         */
        public static final String[] DEFINITIONS_DIR_NAMES = com.walmartlabs.concord.sdk.Constants.Files.DEFINITIONS_DIR_NAMES;

        /**
         * Directory containing process profiles.
         */
        public static final String PROFILES_DIR_NAME = com.walmartlabs.concord.sdk.Constants.Files.PROFILES_DIR_NAME;

        public static final String INSTANCE_ID_FILE_NAME = "_instanceId";

        /**
         * File, containing request data of a payload: process arguments, entry point name, etc.
         */
        public static final String REQUEST_DATA_FILE_NAME = com.walmartlabs.concord.sdk.Constants.Files.REQUEST_DATA_FILE_NAME;

        /**
         * Directory, containing job "attachments": reports, stats, etc.
         */
        public static final String JOB_ATTACHMENTS_DIR_NAME = com.walmartlabs.concord.sdk.Constants.Files.JOB_ATTACHMENTS_DIR_NAME;

        /**
         * Directory, containing process' state.
         */
        public static final String JOB_STATE_DIR_NAME = com.walmartlabs.concord.sdk.Constants.Files.JOB_STATE_DIR_NAME;

        /**
         * Directory, containing process' forms.
         */
        public static final String JOB_FORMS_DIR_NAME = com.walmartlabs.concord.sdk.Constants.Files.JOB_FORMS_DIR_NAME;

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
         * Files, that won't be added to a payload or saved in the state store.
         */
        public static final String[] IGNORED_FILES = {"^\\.git.*"};

        private Files() {
        }
    }

    /**
     * Agent parameters.
     */
    public static final class Agent {

        /**
         * File, containing runtime parameters for agents: heap size, JVM arguments, etc.
         */
        public static final String AGENT_PARAMS_FILE_NAME = "_agent.json";

        /**
         * JVM parameters for an agent's job.
         */
        public static final String JVM_ARGS_KEY = "jvmArgs";

        private Agent() {
        }
    }

    private Constants() {
    }
}
