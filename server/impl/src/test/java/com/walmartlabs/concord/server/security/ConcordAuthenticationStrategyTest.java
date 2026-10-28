package com.walmartlabs.concord.server.security;

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

import com.walmartlabs.concord.server.sdk.security.AuthorizationSubjectNotFoundException;
import com.walmartlabs.concord.server.sdk.security.AuthorizationUnavailableException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.realm.Realm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConcordAuthenticationStrategyTest {
    @Test
    void preservesOnlyAuthorizationFailureMarkers() {
        var strategy = new ConcordAuthenticationStrategy();
        Realm realm = mock(Realm.class);
        AuthenticationInfo aggregate = mock(AuthenticationInfo.class);
        var token = new UsernamePasswordToken("user", "password");
        var unavailable = assertThrows(AuthenticationException.class, () -> strategy.afterAttempt(realm, token, null, aggregate,
                new AuthorizationUnavailableException("offline")));
        assertEquals(503, AuthorizationFailure.status(unavailable));
        var missing = assertThrows(AuthenticationException.class, () -> strategy.afterAttempt(realm, token, null, aggregate,
                new AuthorizationSubjectNotFoundException("missing")));
        assertEquals(403, AuthorizationFailure.status(missing));
        assertSame(aggregate, strategy.afterAttempt(realm, token, null, aggregate, new AuthenticationException("bad credentials")));
        assertEquals(0, AuthorizationFailure.status(new AuthenticationException("bad credentials")));
    }
}
