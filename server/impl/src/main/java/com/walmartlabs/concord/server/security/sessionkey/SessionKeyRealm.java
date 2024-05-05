package com.walmartlabs.concord.server.security.sessionkey;

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

import com.google.common.collect.ImmutableSet;
import com.walmartlabs.concord.server.process.ProcessSecurityContext;
import com.walmartlabs.concord.server.process.queue.ProcessInitiatorEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.security.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Set;

public class SessionKeyRealm extends AuthorizingRealm {

    private static final Logger log = LoggerFactory.getLogger(SessionKeyRealm.class);

    public static final String REALM_NAME = "sessionkey";

    private final ProcessSecurityContext processSecurityContext;
    private final ProcessQueueManager processQueueManager;

    private static final Set<ProcessStatus> FINISHED_STATUSES = ImmutableSet.of(
            ProcessStatus.FINISHED,
            ProcessStatus.FAILED,
            ProcessStatus.CANCELLED,
            ProcessStatus.TIMED_OUT);

    @Inject
    public SessionKeyRealm(ProcessSecurityContext processSecurityContext,
                           ProcessQueueManager processQueueManager) {

        this.processSecurityContext = processSecurityContext;
        this.processQueueManager = processQueueManager;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof SessionKey;
    }

    @Override
    @WithTimer
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        SessionKey t = (SessionKey) token;
        PartialProcessKey processKey = PartialProcessKey.from(t.getInstanceId());

        try {
            ProcessInitiatorEntry p = processQueueManager.getInitiator(processKey);
            if (p == null) {
                log.warn("doGetAuthenticationInfo -> process not found: {}", t.getInstanceId());
                return null;
            }

            if (p.initiatorId() == null) {
                log.warn("doGetAuthenticationInfo -> initiator not found: {}", t.getInstanceId());
                return null;
            }

            if (isFinished(p)) {
                log.warn("doGetAuthenticationInfo -> process is finished: {}", t.getInstanceId());
                return null;
            }

            PrincipalCollection principals = getPrincipals(processKey);
            return new SimpleAccount(principals, t.getInstanceId(), getName());
        } catch (Exception e) {
            log.error("doGetAuthenticationInfo ['{}'] -> error", t.getInstanceId(), e);
            throw e;
        }
    }

    private PrincipalCollection getPrincipals(PartialProcessKey processKey) {
        PrincipalCollection principals = processSecurityContext.getPrincipals(processKey);

        SessionKeyPrincipal p = principals.oneByType(SessionKeyPrincipal.class);
        if (p != null) {
            // should never happen, sessionkey principals shouldn't be stored in the process state
            log.warn("getPrincipals ['{}'] -> unexpected principal: {}", processKey, p.getProcessKey());
            throw new AuthenticationException("Unexpected session principal: " + p.getProcessKey());
        }

        SimplePrincipalCollection c = new SimplePrincipalCollection(principals);
        c.add(new SessionKeyPrincipal(processKey), REALM_NAME);
        return c;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        SessionKeyPrincipal p = principals.oneByType(SessionKeyPrincipal.class);
        if (p == null) {
            return null;
        }
        return new SimpleAuthorizationInfo();
    }

    private boolean isFinished(ProcessInitiatorEntry process) {
        return FINISHED_STATUSES.contains(process.status());
    }
}
