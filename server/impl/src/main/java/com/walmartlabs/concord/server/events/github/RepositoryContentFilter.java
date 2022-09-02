package com.walmartlabs.concord.server.events.github;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.cfg.GitConfiguration;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

import static com.walmartlabs.concord.sdk.Constants.Trigger.REPOSITORY_CONTENT;

@Named
public class RepositoryContentFilter implements ConditionsFilter {

    private static final Logger log = LoggerFactory.getLogger(RepositoryContentFilter.class);

    private final long connectTimeout;
    private final long readTimeout;

    private final String token;

    @Inject
    public RepositoryContentFilter(GitConfiguration cfg, GithubConfiguration githubCfg) {
        this.token = cfg.getOauthToken();
        this.connectTimeout = githubCfg.getContentCheckConnectTimeout().toMillis();
        this.readTimeout = githubCfg.getContentCheckReadTimeout().toMillis();
    }

    @Override
    public int priority() {
        return RepositoryInfoFilter.PRIORITY + 1;
    }

    @Override
    public Map<String, Object> preprocess(Map<String, Object> conditions) {
        if (!conditions.containsKey(REPOSITORY_CONTENT)) {
            return conditions;
        }

        Map<String, Object> result = new HashMap<>(conditions);
        result.remove(REPOSITORY_CONTENT);
        return result;
    }

    @Override
    @WithTimer
    public boolean filter(Payload payload, TriggerEntry trigger, Map<String, Object> triggerConditions, Map<String, Object> event) {
        String apiUrl = (String) ConfigurationUtils.get(payload.raw(), "repository", "contents_url");

        List<Map<String, Object>> conditions = MapUtils.getList(trigger.getConditions(), REPOSITORY_CONTENT, Collections.emptyList());
        for (Map<String, Object> c : conditions) {
            String ref = MapUtils.get(c, "ref", payload.getString("ref"));
            String path = MapUtils.getString(c, "path");
            if (!exists(apiUrl, path, ref)) {
                return false;
            }
        }

        return true;
    }

    private boolean exists(String apiUrl, String path, String ref) {
        HttpURLConnection con = null;
        try {
            path = URLEncoder.encode(path, "utf8").replaceAll("\\+", "%20");
            URL url = new URL(apiUrl.replace("{+path}", path) + "?ref=" + ref);
            con = (HttpURLConnection) url.openConnection();
            con.setRequestProperty("Authorization", Base64.getEncoder().encodeToString(token.getBytes()));
            con.setRequestProperty("Accept", "application/vnd.github+json");
            con.setRequestMethod("GET");
            con.setConnectTimeout((int)connectTimeout);
            con.setReadTimeout((int)readTimeout);
            con.setDoOutput(false);
            int responseCode = con.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            log.warn("exists ['{}', '{}', '{}'] -> error {}", apiUrl, path, ref, e.getMessage());
            return false;
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }
}
