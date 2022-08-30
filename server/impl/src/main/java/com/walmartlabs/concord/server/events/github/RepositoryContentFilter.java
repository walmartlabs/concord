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
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
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

    private static final int CONNECT_TIMEOUT = 10_000;
    private static final int READ_TIMEOUT = 10_000;

    private final String token;

    @Inject
    public RepositoryContentFilter(GitConfiguration cfg) {
        this.token = cfg.getOauthToken();
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
            con.setConnectTimeout(CONNECT_TIMEOUT);
            con.setReadTimeout(READ_TIMEOUT);
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
