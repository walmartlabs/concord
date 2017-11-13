package com.walmartlabs.concord.server.security.sessionkey;

import com.google.common.collect.ImmutableSet;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.process.ProcessSecurityContext;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.security.ConcordShiroAuthorizer;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Set;

@Named
public class SessionKeyRealm extends AuthorizingRealm {

    private final ProcessSecurityContext processSecurityContext;
    private final ProcessQueueDao processQueueDao;
    private final ConcordShiroAuthorizer authorizer;

    private static final Set<ProcessStatus> FINISHED_STATUSES = ImmutableSet.of(
            ProcessStatus.FINISHED,
            ProcessStatus.FAILED,
            ProcessStatus.CANCELLED,
            ProcessStatus.STALLED);

    @Inject
    public SessionKeyRealm(ProcessSecurityContext processSecurityContext,
                           ProcessQueueDao processQueueDao,
                           ConcordShiroAuthorizer authorizer) {
        this.processSecurityContext = processSecurityContext;
        this.processQueueDao = processQueueDao;
        this.authorizer = authorizer;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof SessionKey;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        SessionKey t = (SessionKey) token;

        PrincipalCollection principals = processSecurityContext.getPrincipals(t.getInstanceId());

        ProcessEntry process = processQueueDao.get(t.getInstanceId());
        if (process == null || process.getInitiator() == null || isFinished(process)) {
            return null;
        }

        return new SimpleAccount(principals, t.getInstanceId(), getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        UserPrincipal p = (UserPrincipal) principals.getPrimaryPrincipal();
        if (!"sessionkey".equals(p.getRealm())) {
            return null;
        }
        return authorizer.getAuthorizationInfo(p, null);
    }

    private boolean isFinished(ProcessEntry process) {
        return FINISHED_STATUSES.contains(process.getStatus());
    }
}
