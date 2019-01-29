package com.walmartlabs.concord.server.security;

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

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;

import java.io.*;
import java.util.Optional;

public final class PrincipalUtils {

    public static <T> T getCurrent(Class<T> type) {
        Subject subject = SecurityUtils.getSubject();
        if (subject == null) {
            return null;
        }

        PrincipalCollection principals = subject.getPrincipals();
        if (principals == null) {
            return null;
        }

        return principals.oneByType(type);
    }

    public static <T> T assertCurrent(Class<T> type) {
        T p = getCurrent(type);
        if (p == null) {
            throw new AuthenticationException("Can't determine the current principal (" + type.getName() + ")");
        }
        return p;
    }

    public static byte[] serialize(PrincipalCollection data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public static Optional<PrincipalCollection> deserialize(byte[] data) {
        InputStream in = new ByteArrayInputStream(data);
        return deserialize(in);
    }

    public static Optional<PrincipalCollection> deserialize(InputStream in) {
        try (ObjectInputStream ois = new ObjectInputStream(in)) {
            return Optional.of((PrincipalCollection) ois.readObject());
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private PrincipalUtils() {
    }
}
