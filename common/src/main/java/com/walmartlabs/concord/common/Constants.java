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
     * File containing request data of a payload: process arguments, entry point name, etc.
     */
    public static final String REQUEST_DATA_FILE_NAME = "_main.json";

    /**
     * Process entry point.
     */
    public static final String ENTRY_POINT_KEY = "entryPoint";

    private Constants() {
    }
}
