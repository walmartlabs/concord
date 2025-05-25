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

/**
 * Implementation-specific constants.
 * @deprecated will be removed after the v2 runtime introduction.
 */
public final class InternalConstants extends Constants {

    /**
     * Left for backward-compatibility.
     */
    public static final class Context extends Constants.Context {
    }

    /**
     * Left for backward-compatibility.
     */
    public static final class Request extends Constants.Request {
    }

    /**
     * Left for backward-compatibility.
     */
    public static final class Files extends Constants.Files {
    }

    /**
     * Left for backward-compatibility.
     */
    public static final class Flows extends Constants.Flows {
    }

    /**
     * Left for backward-compatibility.
     */
    public static final class Agent extends Constants.Agent {
    }

    /**
     * Left for backward-compatibility.
     */
    public static final class Forms extends Constants.Forms {
    }

    /**
     * Left for backward-compatibility.
     */
    public static final class Headers extends Constants.Headers {
    }
}
