package com.walmartlabs.concord.process.loader;

public final class StandardRuntimeTypes {

    public static final String CONCORD_V1_RUNTIME_TYPE = "concord-v1";
    public static final String CONCORD_V2_RUNTIME_TYPE = "concord-v2";

    /**
     * Files that the runtime considers "root" project files.
     */
    public static final String[] PROJECT_ROOT_FILE_NAMES = {
            ".concord.yml",
            "concord.yml",
            ".concord.yaml",
            "concord.yaml"
    };


    private StandardRuntimeTypes() {
    }
}
