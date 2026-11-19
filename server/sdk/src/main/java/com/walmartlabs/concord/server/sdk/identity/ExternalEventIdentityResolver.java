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

/**
 * Resolves the actor of an external event, not its recipient, installation,
 * resource owner or commit author. Implementations must be thread-safe.
 */
public interface ExternalEventIdentityResolver {

    /**
     * Returns a non-null resolution. Use {@code Unhandled} only when this resolver
     * does not own the actor, never for a failed lookup of a recognized actor.
     * Endpoint authentication alone does not necessarily verify payload actor
     * claims; use the context's authentication information when verifying them.
     *
     * <p>Multiple resolvers claiming an actor are an error, not a priority order.
     * A resolved reference must still pass authoritative identity lookup,
     * lifecycle and freshness checks before normal Concord authorization.
     *
     * @throws ExternalIdentityException if actor verification cannot be completed
     */
    ExternalIdentityResolution resolve(ExternalEventContext context) throws ExternalIdentityException;
}
