package com.walmartlabs.concord.server.security.rememberme;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.server.cfg.RememberMeConfiguration;
import com.walmartlabs.concord.server.security.SecurityUtils;
import com.walmartlabs.concord.server.security.apikey.ApiKey;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.lang.io.SerializationException;
import org.apache.shiro.lang.io.Serializer;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.mgt.CookieRememberMeManager;
import org.apache.shiro.web.util.WebUtils;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/**
 * Implementation of {@link org.apache.shiro.mgt.RememberMeManager}. Uses the DB to store session data.
 */
public class ConcordRememberMeManager extends CookieRememberMeManager {

    @Inject
    public ConcordRememberMeManager(RememberMeConfiguration cfg) {
        byte[] cipherKey = cfg.getCipherKey();
        if (cipherKey != null) {
            if (cipherKey.length != 16 && cipherKey.length != 24 && cipherKey.length != 32) {
                throw new IllegalArgumentException("Invalid rememberMe.cipherKey value: should be 16, 24 or 32 bytes length");
            }
            setCipherKey(cipherKey);
        }

        int maxAge = (int) cfg.getRememberMeMaxAge().getSeconds();
        getCookie().setMaxAge(maxAge);

        setSerializer(new PrincipalCollectionSerializer());
    }

    @Override
    protected void rememberIdentity(Subject subject, PrincipalCollection src) {
        SimplePrincipalCollection dst = new SimplePrincipalCollection();

        // keep only the specific types of principals to keep the cookie small
        for (String realmName : src.getRealmNames()) {
            Collection<?> principals = src.fromRealm(realmName);
            for (Object p : principals) {
                if (p instanceof UsernamePasswordToken || p instanceof ApiKey) {
                    dst.add(p, realmName);
                }
            }
        }

        super.rememberIdentity(subject, dst);
    }

    @Override
    protected void forgetIdentity(Subject subject) {
        if (!WebUtils.isHttp(subject)) {
            return;
        }

        // delete the "remember me" cookie only if it is present
        var request = WebUtils.getHttpRequest(subject);
        var rememberMeCookieName = getCookie().getName();

        Optional.ofNullable(request.getCookies()).stream()
                .flatMap(Arrays::stream)
                .filter(cookie -> cookie.getName().equals(rememberMeCookieName))
                .findFirst()
                .ifPresent(cookie -> super.forgetIdentity(subject));
    }

    private static class PrincipalCollectionSerializer implements Serializer<PrincipalCollection> {
        @Override
        public byte[] serialize(PrincipalCollection principalCollection) throws SerializationException {
            return SecurityUtils.serialize(principalCollection);
        }

        @Override
        public PrincipalCollection deserialize(byte[] bytes) throws SerializationException {
            return SecurityUtils.deserialize(bytes).orElse(null);
        }
    }
}
