package com.walmartlabs.concord.common;

public final class Constants {

    /**
     * Process definition attribute: path to a local directory, containing agent's payload
     */
    public static final String LOCAL_PATH_ATTR = "localPath";

    /**
     * Execution context variable: path to a local directory, containing agent's payload.
     */
    public static final String LOCAL_PATH_KEY = "__attr_localPath";

    /**
     * ID of the current process instance.
     */
    public static final String TX_ID_KEY = "txId";

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
     * File, containing runtime parameters for agents: heap size, JVM arguments, etc.
     */
    public static final String AGENT_PARAMS_FILE_NAME = "_agent.json";

    /**
     * JVM parameters for an agent's job.
     */
    public static final String JVM_ARGS_KEY = "jvmArgs";

    /**
     * Process entry point.
     */
    public static final String ENTRY_POINT_KEY = "entryPoint";

    /**
     * Directory, containing job "attachments": reports, stats, etc.
     */
    public static final String JOB_ATTACHMENTS_DIR_NAME = "_attachments";

    private Constants() {
    }
}
