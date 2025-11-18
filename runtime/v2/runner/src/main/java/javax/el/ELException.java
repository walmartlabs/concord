/*
 * Copyright (c) 1997-2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.el;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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
 * Represents any of the exception conditions that can arise during
 * expression evaluation.
 *
 * @since JSP 2.1
 */
public class ELException extends RuntimeException {

    //-------------------------------------
    /**
     * Creates an <code>ELException</code> with no detail message.
     */
    public ELException () {
        super ();
    }

    //-------------------------------------
    /**
     * Creates an <code>ELException</code> with the provided detail message.
     *
     * @param pMessage the detail message
     */
    public ELException (String pMessage) {
        super (pMessage);
    }

    //-------------------------------------
    /**
     * Creates an <code>ELException</code> with the given cause.
     *
     * @param pRootCause the originating cause of this exception
     */
    public ELException (Throwable pRootCause) {
        super( pRootCause );
    }

    //-------------------------------------
    /**
     * Creates an ELException with the given detail message and root cause.
     *
     * @param pMessage the detail message
     * @param pRootCause the originating cause of this exception
     */
    public ELException (String pMessage,
                        Throwable pRootCause) {
        super (pMessage, pRootCause);
    }

}
