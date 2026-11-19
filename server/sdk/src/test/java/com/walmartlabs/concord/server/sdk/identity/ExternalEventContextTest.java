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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ExternalEventContextTest {

    @Test
    public void testAuthenticationContextIsSeparateFromPayload() {
        var payload = "{\"authority\":\"untrusted\",\"sender\":{\"id\":123}}";
        var context = new ExternalEventContext("github", "push", "delivery", "configured-authority",
                ExternalEventContext.Authentication.ENDPOINT_CREDENTIAL, payload);

        assertEquals("github", context.source());
        assertEquals("push", context.eventType());
        assertEquals("delivery", context.deliveryId());
        assertEquals("configured-authority", context.authority());
        assertEquals(ExternalEventContext.Authentication.ENDPOINT_CREDENTIAL, context.authentication());
        assertEquals(payload, context.payload());
    }

    @Test
    public void testDeliveryIdIsOptional() {
        var context = new ExternalEventContext("github", "push", null, "authority",
                ExternalEventContext.Authentication.SIGNED_PAYLOAD, "{}");

        assertNull(context.deliveryId());
        assertEquals(ExternalEventContext.Authentication.SIGNED_PAYLOAD, context.authentication());
    }

    @Test
    public void testRequiredContext() {
        assertThrows(IllegalArgumentException.class, () -> new ExternalEventContext(" ", "push", null, "authority",
                ExternalEventContext.Authentication.SIGNED_PAYLOAD, "{}"));
        assertThrows(IllegalArgumentException.class, () -> new ExternalEventContext("github", "", null, "authority",
                ExternalEventContext.Authentication.SIGNED_PAYLOAD, "{}"));
        assertThrows(IllegalArgumentException.class, () -> new ExternalEventContext("github", "push", null, "",
                ExternalEventContext.Authentication.SIGNED_PAYLOAD, "{}"));
        assertThrows(NullPointerException.class, () -> new ExternalEventContext("github", "push", null, "authority",
                null, "{}"));
        assertThrows(NullPointerException.class, () -> new ExternalEventContext("github", "push", null, "authority",
                ExternalEventContext.Authentication.SIGNED_PAYLOAD, null));
        assertThrows(IllegalArgumentException.class, () -> new ExternalEventContext("github", "push", null, "authority",
                ExternalEventContext.Authentication.SIGNED_PAYLOAD, " "));
    }
}
