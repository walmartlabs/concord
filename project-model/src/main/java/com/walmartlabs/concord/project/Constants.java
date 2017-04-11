package com.walmartlabs.concord.project;

import io.takari.bpm.model.ProcessDefinition;

/**
 * Project and process constants.
 */
public final class Constants {

    /**
     * Process context variables.
     */
    public static final class Context {

        /**
         * Process definition attribute: path to a local directory, containing agent's payload
         */
        public static final String LOCAL_PATH_ATTR = "localPath";

        /**
         * Execution context variable: path to a local directory, containing agent's payload.
         */
        public static final String LOCAL_PATH_KEY = ProcessDefinition.ATTRIBUTE_KEY_PREFIX + LOCAL_PATH_ATTR;

        /**
         * ID of the current process instance.
         */
        public static final String TX_ID_KEY = "txId";

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
         * Active profiles.
         */
        public static final String ACTIVE_PROFILES_KEY = "activeProfiles";

        /**
         * Template name.
         */
        public static final String TEMPLATE_KEY = "template";

        private Request() {
        }
    }

    /**
     * Project files and directories.
     */
    public static final class Files {

        /**
         * Directory containing libraries (dependencies) of a payload.
         */
        public static final String LIBRARIES_DIR_NAME = "lib";

        /**
         * Directory containing process definitions of a payload.
         */
        public static final String DEFINITIONS_DIR_NAME = "processes";

        /**
         * File, containing request data of a payload: process arguments, entry point name, etc.
         */
        public static final String REQUEST_DATA_FILE_NAME = "_main.json";

        /**
         * Default request data. File that will be merged with {@link #REQUEST_DEFAULTS_FILE_NAME}'s JSON.
         */
        public static final String REQUEST_DEFAULTS_FILE_NAME = "_defaults.json";

        /**
         * Directory, containing job "attachments": reports, stats, etc.
         */
        public static final String JOB_ATTACHMENTS_DIR_NAME = "_attachments";

        /**
         * Directory, containing process' state.
         */
        public static final String JOB_STATE_DIR_NAME = "_state";

        /**
         * Directory, containing process' forms.
         */
        public static final String JOB_FORMS_DIR_NAME = "forms";

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
