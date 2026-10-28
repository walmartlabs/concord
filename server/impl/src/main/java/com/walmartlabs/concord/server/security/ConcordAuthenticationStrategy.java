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

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.pam.AtLeastOneSuccessfulStrategy;
import org.apache.shiro.realm.Realm;

/** Preserve authorization provisioning failures instead of letting PAM discard them. */
public final class ConcordAuthenticationStrategy extends AtLeastOneSuccessfulStrategy {
    @Override
    public AuthenticationInfo afterAttempt(Realm realm, AuthenticationToken token, AuthenticationInfo info,
                                           AuthenticationInfo aggregate, Throwable failure) {
        if (AuthorizationFailure.status(failure) != 0) {
            throw new AuthenticationException("Authorization provisioning failed", failure);
        }
        return super.afterAttempt(realm, token, info, aggregate, failure);
    }

    @Override
    public AuthenticationInfo afterAllAttempts(AuthenticationToken token, AuthenticationInfo aggregate) {
        AuthenticationInfo result = super.afterAllAttempts(token, aggregate);
        PrincipalNormalization.find(result.getPrincipals());
        return result;
    }
}
