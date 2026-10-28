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

import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

import javax.inject.Inject;

/** Projects current Concord grants independently of the realm that authenticated the subject. */
public class ConcordAuthorizationRealm extends AuthorizingRealm {
    private final AuthorizationService authorization;

    @Inject
    public ConcordAuthorizationRealm(AuthorizationService authorization) {
        this.authorization = authorization;
        setName("concordAuthorization");
        setAuthorizationCachingEnabled(false);
        setAuthenticationCachingEnabled(false);
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return false;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
        return null;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        SubjectPrincipal subject = PrincipalNormalization.find(principals);
        if (subject == null) {
            return new SimpleAuthorizationInfo();
        }
        AuthorizationService.EffectiveAuthorization grants = AuthorizationScope.current(subject.userId());
        if (grants == null) {
            grants = authorization.prepare(subject.userId());
        }
        SimpleAuthorizationInfo result = new SimpleAuthorizationInfo(grants.roles());
        result.setStringPermissions(grants.permissions());
        return result;
    }
}
