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
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.security.PrincipalUtils;
import org.apache.shiro.io.SerializationException;
import org.apache.shiro.io.Serializer;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.SubjectContext;
import org.apache.shiro.web.mgt.CookieRememberMeManager;

import javax.inject.Inject;
import javax.inject.Named;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Implementation of {@link org.apache.shiro.mgt.RememberMeManager}. Uses the DB to store session data.
 */
@Named
public class ConcordRememberMeManager extends CookieRememberMeManager {

    private final SecureRandom rng;
    private final CookieStoreDao storeDao;

    @Inject
    public ConcordRememberMeManager(RememberMeConfiguration cfg, SecureRandom rng, CookieStoreDao storeDao) {
        this.rng = rng;
        this.storeDao = storeDao;

        byte[] cipherKey = cfg.getCipherKey();
        if (cipherKey != null) {
            if (cipherKey.length != 16 && cipherKey.length != 24 && cipherKey.length != 32) {
                throw new IllegalArgumentException("Invalid rememberMe.cipherKey value: should be 16, 24 or 32 bytes length");
            }
            setCipherKey(cipherKey);
        }

        setSerializer(new PrincipalCollectionSerializer());
    }

    @Override
    @WithTimer
    protected void rememberSerializedIdentity(Subject subject, byte[] serialized) {
        byte[] key = new byte[128];
        rng.nextBytes(key);
        storeDao.insert(hash(key), serialized);
        super.rememberSerializedIdentity(subject, key);
    }

    @Override
    @WithTimer
    protected byte[] getRememberedSerializedIdentity(SubjectContext subjectContext) {
        byte[] key = super.getRememberedSerializedIdentity(subjectContext);
        if (key == null) {
            return null;
        }
        return storeDao.get(hash(key));
    }

    private static byte[] hash(byte[] ab) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        return md.digest(ab);
    }

    private static class PrincipalCollectionSerializer implements Serializer<PrincipalCollection> {
        @Override
        public byte[] serialize(PrincipalCollection principalCollection) throws SerializationException {
            return PrincipalUtils.serialize(principalCollection);
        }

        @Override
        public PrincipalCollection deserialize(byte[] bytes) throws SerializationException {
            return PrincipalUtils.deserialize(bytes).orElse(null);
        }
    }
}
