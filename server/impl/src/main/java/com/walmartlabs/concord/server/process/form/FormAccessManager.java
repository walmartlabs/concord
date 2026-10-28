package com.walmartlabs.concord.server.process.form;

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

import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserInfoProvider;
import com.walmartlabs.concord.server.user.UserManager;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormAccessManager {

    private static final Pattern GROUP_PATTERN = Pattern.compile("CN=(.*?),", Pattern.CASE_INSENSITIVE);

    private final UserManager userManager;

    @Inject
    public FormAccessManager(UserManager userManager) {
        this.userManager = userManager;
    }

    public void assertFormAccess(String formName, Map<String, Serializable> runAsParams) {
        if (runAsParams == null || runAsParams.isEmpty()) {
            return;
        }

        UserPrincipal p = UserPrincipal.assertCurrent();

        Set<String> expectedUsers = com.walmartlabs.concord.forms.FormUtils.getRunAsUsers(formName, runAsParams);
        if (!expectedUsers.isEmpty() && !expectedUsers.contains(p.getUsername())) {
            throw new UnauthorizedException("The current user (" + p.getUsername() + ") doesn't have " +
                    "the necessary permissions to access the form.");
        }

        Set<String> formRunAsGroups = com.walmartlabs.concord.forms.FormUtils.getRunAsLdapGroups(formName, runAsParams);
        if (!formRunAsGroups.isEmpty()) {
            Set<String> userLdapGroups = Optional.ofNullable(userManager.getCurrentUserInfo())
                    .map(UserInfoProvider.UserInfo::groups)
                    .orElseGet(Set::of);

            boolean isGroupMatched = formRunAsGroups.stream()
                    .anyMatch(group -> matchesLdapGroup(group, userLdapGroups));

            if (!isGroupMatched) {
                throw new UnauthorizedException("The current user (" + p.getUsername() + ") doesn't have " +
                        "the necessary permissions to resume process. Expected LDAP group(s) '" + formRunAsGroups + "'");
            }
        }
    }

    private static boolean matchesLdapGroup(String pattern, Set<String> userLdapGroups) {
        if (userLdapGroups == null) {
            return false;
        }

        String normalizedPattern = normalizeGroup(pattern);
        boolean isLdapGroupPattern = checkLdapGroupPattern(pattern);

        return userLdapGroups.stream()
                .anyMatch(g -> {
                    if (isLdapGroupPattern && checkLdapGroupPattern(g)) {
                        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(g).matches();
                    }
                    return normalizedPattern.equalsIgnoreCase(normalizeGroup(g));
                });
    }

    private static boolean checkLdapGroupPattern(String group) {
        // memberOf attribute values(groups) starts with CN
        return group.toUpperCase().trim().startsWith("CN=");
    }

    private static String normalizeGroup(String group) {
        Matcher matcher = GROUP_PATTERN.matcher(group);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return group;
    }
}
