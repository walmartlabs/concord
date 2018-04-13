package com.walmartlabs.concord.server.security.sessionkey;

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

import com.google.common.collect.ImmutableSet;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.process.ProcessSecurityContext;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Set;
import java.util.UUID;

@Named
public class SessionKeyRealm extends AuthorizingRealm {

    public static final String REALM_NAME = "sessionkey";

    private final ProcessSecurityContext processSecurityContext;
    private final ProcessQueueDao processQueueDao;

    private static final Set<ProcessStatus> FINISHED_STATUSES = ImmutableSet.of(
            ProcessStatus.FINISHED,
            ProcessStatus.FAILED,
            ProcessStatus.CANCELLED,
            ProcessStatus.STALLED);

    @Inject
    public SessionKeyRealm(ProcessSecurityContext processSecurityContext,
                           ProcessQueueDao processQueueDao) {
        this.processSecurityContext = processSecurityContext;
        this.processQueueDao = processQueueDao;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof SessionKey;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        SessionKey t = (SessionKey) token;

        ProcessEntry process = processQueueDao.get(t.getInstanceId());
        if (process == null || process.getInitiator() == null || isFinished(process)) {
            return null;
        }

        PrincipalCollection principals = getPrincipals(t.getInstanceId());
        return new SimpleAccount(principals, t.getInstanceId(), getName());
    }

    private PrincipalCollection getPrincipals(UUID instanceId) {
        PrincipalCollection principals = processSecurityContext.getPrincipals(instanceId);

        SessionKeyPrincipal p = principals.oneByType(SessionKeyPrincipal.class);
        if (p != null) {
            if (!instanceId.equals(p.getProcessInstanceId())) {
                throw new AuthenticationException("Session key mismatch, expected " + p.getProcessInstanceId() + ", got " + instanceId);
            }
        } else {
            SimplePrincipalCollection c = new SimplePrincipalCollection(principals);
            c.add(new SessionKeyPrincipal(instanceId), REALM_NAME);
            return c;
        }

        return principals;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        SessionKeyPrincipal p = principals.oneByType(SessionKeyPrincipal.class);
        if (p == null) {
            return null;
        }
        return new SimpleAuthorizationInfo();
    }

    private boolean isFinished(ProcessEntry process) {
        return FINISHED_STATUSES.contains(process.getStatus());
    }
}
