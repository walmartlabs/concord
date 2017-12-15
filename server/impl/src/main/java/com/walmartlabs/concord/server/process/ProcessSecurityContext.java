package com.walmartlabs.concord.server.process;

import com.google.common.base.Throwables;
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

@Named
public class ProcessSecurityContext {

    private static final String PRINCIPAL_FILE_PATH = ".concord/initiator";

    private final ProcessStateManager stateManager;
    private final Injector injector;

    @Inject
    public ProcessSecurityContext(ProcessStateManager stateManager, Injector injector) {
        this.stateManager = stateManager;
        this.injector = injector;
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
        return stateManager.get(instanceId, PRINCIPAL_FILE_PATH, ProcessSecurityContext::deserialize).orElse(null);
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
