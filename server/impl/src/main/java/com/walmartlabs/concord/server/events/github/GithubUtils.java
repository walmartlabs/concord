package com.walmartlabs.concord.server.events.github;

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


import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;

import java.net.URI;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GithubUtils {

    /**
     * Same rules as used by git in shorten_unambiguous_ref
     * see: https://github.com/git/git/blob/v2.19.1/refs.c#L483
     */
    private static final Pattern[] REF_PARSE_RULES = {
            Pattern.compile("^(.*)$"),
            Pattern.compile("^refs/(.*)$"),
            Pattern.compile("^refs/tags/(.*)$"),
            Pattern.compile("^refs/heads/(.*)$"),
            Pattern.compile("^refs/remotes/(.*)$"),
            Pattern.compile("^refs/remotes/(.*)/HEAD$")
    };

    public static String getRefShortName(String ref) {
        String str = ref.trim();
        String result = str;
        for (Pattern p : REF_PARSE_RULES) {
            Matcher m = p.matcher(str);
            if (m.matches()) {
                result = m.group(1);
            }
        }
        return result;
    }

    public static String getRepositoryName(String repoUrl) {
        GithubRepoInfo info = getRepositoryInfo(repoUrl);
        if (info == null) {
            return null;
        }
        return info.owner() + "/" + info.name();
    }

    public static GithubRepoInfo getRepositoryInfo(String repoUrl) {
        String repoPath = getRepoPath(repoUrl);

        String[] u = repoPath.split("/");
        if (u.length < 2) {
            // a file path perhaps?
            return null;
        }

        return GithubRepoInfo.builder()
                .owner(owner(u[0]))
                .name(name(u[1]))
                .build();
    }

    /**
     * Returns true if the specified event and its payload is an "empty" {@code push} event.
     */
    public static boolean isEmptyPush(String eventName, Payload payload) {
        if (!Constants.PUSH_EVENT.equals(eventName)) {
            return false;
        }

        return Objects.equals(payload.raw().get("after"), payload.raw().get("before"));
    }

    /**
     * Returns the value of the trigger's {@code ignoreEmptyPush} parameter
     * or {@code true} if it is not defined.
     */
    public static boolean ignoreEmptyPush(TriggerEntry triggerEntry) {
        return MapUtils.getBoolean(triggerEntry.getCfg(), Constants.IGNORE_EMPTY_PUSH_KEY, true);
    }

    private static String getRepoPath(String repoUrl) {
        // tests support
        URI uri = URI.create(repoUrl);

        if(uri.getScheme().equals("file")) {
            String[] folders = uri.getPath().split("/");
            if(folders.length < 2) {
                return repoUrl;
            }
            return folders[folders.length - 2] + "/" + folders[folders.length - 1];
        }

        return uri.getPath();
    }

    private static String name(String str) {
        return str.replaceAll("^\\W+|\\.git$", "");
    }

    private static String owner(String str) {
        int idx = str.indexOf(':');
        if (idx > 0) {
            return str.substring(idx + 1);
        }
        return str;
    }

    private static boolean isPort(String str) {
        try {
            int port = Integer.parseInt(str);
            return port > 0 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private GithubUtils() {
    }
}
