package com.walmartlabs.concord.server.sdk.identity;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Server-created event context. Authority and authentication information must be
 * established by the server, not copied from untrusted payload fields.
 *
 * @param source event integration, for example {@code github}
 * @param eventType source-specific event type, for example {@code push}
 * @param deliveryId optional delivery identifier; not proof of authenticity
 * @param authority canonical source instance selected by trusted server configuration
 * @param authentication how the server authenticated this delivery
 * @param payload JSON request body, represented as an immutable string rather than
 *                a shared mutable object tree; may contain sensitive data
 */
public record ExternalEventContext(String source,
                                   String eventType,
                                   @Nullable String deliveryId,
                                   String authority,
                                   Authentication authentication,
                                   String payload) {

    public ExternalEventContext {
        ExternalIdentityRef.requireNonBlank(source, "source");
        ExternalIdentityRef.requireNonBlank(eventType, "eventType");
        ExternalIdentityRef.requireNonBlank(authority, "authority");
        Objects.requireNonNull(authentication, "authentication");
        ExternalIdentityRef.requireNonBlank(payload, "payload");
    }

    public enum Authentication {
        /** The server verified a trusted signature covering the request body. */
        SIGNED_PAYLOAD,
        /**
         * An endpoint credential was accepted, but the body has no verified
         * source signature. Actor claims require independent verification.
         */
        ENDPOINT_CREDENTIAL
    }
}
