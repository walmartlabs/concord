package com.walmartlabs.concord.project;

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

import com.walmartlabs.concord.sdk.Constants;
import io.takari.bpm.model.ProcessDefinition;

/**
 * Implementation-specific constants.
 */
public final class InternalConstants extends Constants {

    public static final class Context extends Constants.Context {

        /**
         * Process definition attribute: path to a local directory, containing agent's payload
         *
         * @see Constants.Context#WORK_DIR_KEY
         * @deprecated use {@link #WORK_DIR_KEY} directly in a flow
         */
        @Deprecated
        public static final String LOCAL_PATH_ATTR = "localPath";

        /**
         * Execution context variable: path to a local directory, containing agent's payload.
         *
         * @see Constants.Context#WORK_DIR_KEY
         * @deprecated use {@link #WORK_DIR_KEY} directly in a flow
         */
        @Deprecated
        public static final String LOCAL_PATH_KEY = ProcessDefinition.ATTRIBUTE_KEY_PREFIX + LOCAL_PATH_ATTR;

        /**
         * Execution context.
         *
         * @deprecated use {@link Constants.Context#CONTEXT_KEY}
         */
        @Deprecated
        public static final String EXECUTION_CONTEXT_KEY = "execution";

        public static final String EVENT_CORRELATION_KEY = "__eventCorrelationId";
    }

    public static final class Request extends Constants.Request {
    }

    /**
     * Project files and directories.
     */
    public static final class Files extends Constants.Files {

        /**
         * Directory which contains payload data.
         */
        public static final String PAYLOAD_DIR_NAME = "payload";

        /**
         * File which contains the ID of a process.
         */
        public static final String INSTANCE_ID_FILE_NAME = "_instanceId";

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
         * File which contains data of process' OUT variables.
         */
        public static final String OUT_VALUES_FILE_NAME = "out.json";

        /**
         * Directory which contains submitted form files.
         */
        public static final String FORM_FILES = "_form_files";

        /**
         * Policy file.
         */
        public static final String POLICY_FILE_NAME = "policy.json";
    }

    public static final class Flows extends Constants.Flows {
    }

    /**
     * Agent parameters.
     */
    public static final class Agent {

        /**
         * File which contains runtime parameters for agents: heap size, JVM arguments, etc.
         */
        public static final String AGENT_PARAMS_FILE_NAME = "_agent.json";

        /**
         * JVM parameters for an agent's job.
         */
        public static final String JVM_ARGS_KEY = "jvmArgs";

        private Agent() {
        }
    }

    public static final class Forms {

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

    public static final class Headers {

        public static final String SECRET_TYPE = "X-Concord-SecretType";

        public static final String AGENT = "X-Concord-Agent";
    }

    public static final class Policy {

        public static final String PROCESS_CFG = "processCfg";
    }
}
