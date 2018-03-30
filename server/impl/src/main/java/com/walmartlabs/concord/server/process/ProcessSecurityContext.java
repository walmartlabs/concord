package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Injector;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.SubjectContext;
import org.apache.shiro.subject.support.DefaultSubjectContext;
import org.apache.shiro.util.ThreadContext;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.*;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Named
public class ProcessSecurityContext {

    private static final String PRINCIPAL_FILE_PATH = ".concord/initiator";

    private final ProcessStateManager stateManager;
    private final Injector injector;
    private final Cache<UUID, PrincipalCollection> principalCache;

    @Inject
    public ProcessSecurityContext(ProcessStateManager stateManager, Injector injector) {
        this.stateManager = stateManager;
        this.injector = injector;
        this.principalCache = CacheBuilder.newBuilder()
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .build();
    }

    public void storeCurrentSubject(UUID instanceId) {
        Subject s = SecurityUtils.getSubject();
        PrincipalCollection ps = s.getPrincipals();
        stateManager.transaction(tx -> {
            stateManager.delete(tx, instanceId, PRINCIPAL_FILE_PATH);
            stateManager.insert(tx, instanceId, PRINCIPAL_FILE_PATH, serialize(ps));
        });
    }

    public PrincipalCollection getPrincipals(UUID instanceId) {
        try {
            return principalCache.get(instanceId,
                    () -> stateManager.get(instanceId, PRINCIPAL_FILE_PATH, ProcessSecurityContext::deserialize).orElse(null));
        } catch (ExecutionException e) {
            throw Throwables.propagate(e);
        }
    }

    public <T> T runAsInitiator(UUID instanceId, Callable<T> c) throws Exception {
        PrincipalCollection principals = getPrincipals(instanceId);
        if (principals == null) {
            throw new UnauthorizedException("Process' principal not found");
        }

        SecurityManager securityManager = injector.getInstance(SecurityManager.class);
        ThreadContext.bind(securityManager);

        SubjectContext ctx = new DefaultSubjectContext();
        ctx.setAuthenticated(true);
        ctx.setPrincipals(principals);

        try {
            Subject subject = securityManager.createSubject(ctx);
            ThreadContext.bind(subject);
            return c.call();
        } finally {
            ThreadContext.unbindSubject();
        }
    }

    private static byte[] serialize(PrincipalCollection principals) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(principals);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return baos.toByteArray();
    }

    private static Optional<PrincipalCollection> deserialize(InputStream in) {
        try (ObjectInputStream ois = new ObjectInputStream(in)) {
            return Optional.of((PrincipalCollection) ois.readObject());
        } catch (IOException | ClassNotFoundException e) {
            throw Throwables.propagate(e);
        }
    }
}
