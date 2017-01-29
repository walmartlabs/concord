package com.walmartlabs.concord.plugins.ansible.inventory;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.support.SubjectThreadState;
import org.apache.shiro.util.LifecycleUtils;
import org.apache.shiro.util.ThreadState;
import org.junit.AfterClass;

public abstract class AbstractShiroTest {

    private static ThreadState subjectThreadState;

    protected void setSubject(Subject subject) {
        clearSubject();
        subjectThreadState = new SubjectThreadState(subject);
        subjectThreadState.bind();
    }

    protected void clearSubject() {
        doClearSubject();
    }

    private static void doClearSubject() {
        if (subjectThreadState != null) {
            subjectThreadState.clear();
            subjectThreadState = null;
        }
    }

    @AfterClass
    public static void tearDownShiro() {
        doClearSubject();
        try {
            SecurityManager securityManager = SecurityUtils.getSecurityManager();
            LifecycleUtils.destroy(securityManager);
        } catch (UnavailableSecurityManagerException ignored) {
        }
        SecurityUtils.setSecurityManager(null);
    }
}
