package com.walmartlabs.concord.server.process.pipelines.processors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapInfo;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.NamingException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Named
public class UserInfoProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(UserInfoProcessor.class);

    private final LdapManager ldapManager;

    @Inject
    public UserInfoProcessor(LdapManager ldapManager) {
        this.ldapManager = ldapManager;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        // collect and store the initiator's data

        UserInfo info = getInfo();
        payload = payload.mergeValues(Payload.REQUEST_DATA_MAP,
                Collections.singletonMap(InternalConstants.Request.INITIATOR_KEY, info));

        log.info("process ['{}'] -> done", payload.getInstanceId());
        return chain.process(payload);
    }

    private UserInfo getInfo() {
        Subject subject = SecurityUtils.getSubject();
        if (subject == null || !subject.isAuthenticated()) {
            return null;
        }

        UserPrincipal p = (UserPrincipal) subject.getPrincipal();
        if (p == null) {
            return null;
        }

        LdapInfo ldapInfo = p.getLdapInfo();
        if (ldapInfo == null) {
            try {
                ldapInfo = ldapManager.getInfo(p.getUsername());
            } catch (NamingException e) {
                log.warn("getInfo -> error while retrieving LDAP information for '{}': {}", p.getUsername(), e.getMessage());
            }
        }

        if (ldapInfo != null) {
            return new UserInfo(p.getUsername(), ldapInfo.getDisplayName(), ldapInfo.getGroups(), ldapInfo.getAttributes());
        } else {
            return new UserInfo(p.getUsername(), p.getUsername(), Collections.emptySet(), Collections.emptyMap());
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static class UserInfo implements Serializable {

        private final String username;
        private final String displayName;
        private final Set<String> groups;
        private final Map<String, String> attributes;

        public UserInfo(String username, String displayName, Set<String> groups, Map<String, String> attributes) {
            this.username = username;
            this.displayName = displayName;
            this.groups = groups;
            this.attributes = attributes;
        }

        public String getUsername() {
            return username;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Set<String> getGroups() {
            return groups;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }
    }
}
