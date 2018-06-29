package com.walmartlabs.concord.plugins.ansible;

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

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.SecretService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

public class GroupVarsProcessor {

    private final Logger log = LoggerFactory.getLogger(GroupVarsProcessor.class);

    private final SecretService secretService;

    private final Context context;

    private final Collection<Path> exportedFiles = new HashSet<>();

    public GroupVarsProcessor(SecretService secretService, Context context) {
        this.secretService = secretService;
        this.context = context;
    }

    @SuppressWarnings("unchecked")
    public void process(String instanceId, Map<String, Object> args, Path workDir) throws Exception {
        Collection<Ref> refs = toRefs(args);
        if (refs == null) {
            return;
        }

        String playbook = (String) args.get(AnsibleConstants.PLAYBOOK_KEY);
        Path playbookPath = workDir.resolve(playbook);

        Path groupVarsBase = playbookPath.getParent().resolve("group_vars");
        if (Files.notExists(groupVarsBase)) {
            Files.createDirectories(groupVarsBase);
        }

        for (Ref r : refs) {
            export(instanceId, workDir, r, groupVarsBase);
        }
    }

    public void postProcess() throws Exception {
        for (Path p : exportedFiles) {
            Files.deleteIfExists(p);
            log.info("Removed exported file {}", p);
        }
    }

    private void export(String instanceId, Path workDir, Ref r, Path groupVarsBase) throws Exception {
        String tmp = secretService.exportAsFile(context, instanceId, workDir.toString(), r.orgName, r.secretName, r.password);

        Path src = Paths.get(tmp);
        Path dst = groupVarsBase.resolve(r.groupName + "." + r.type);
        if (Files.exists(dst)) {
            throw new IllegalArgumentException("Can't export a group_vars file, the destination file already exists: " + dst);
        }

        Files.move(src, dst);
        exportedFiles.add(dst);

        log.info("Exported secret '{}' into {}", r.secretName, dst);
    }

    @SuppressWarnings("unchecked")
    private static Collection<Ref> toRefs(Map<String, Object> args) {
        Object v = args.get(AnsibleConstants.GROUP_VARS_KEY);
        if (v == null) {
            return null;
        }

        if (!(v instanceof Collection)) {
            throw new IllegalArgumentException("'" + AnsibleConstants.GROUP_VARS_KEY + "' must be a list of group_vars references");
        }

        Collection<Object> refs = (Collection<Object>) v;
        if (refs.isEmpty()) {
            return null;
        }

        return refs.stream()
                .map(GroupVarsProcessor::toRef)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static Ref toRef(Object o) {
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException("Unsupported object type in '" + AnsibleConstants.GROUP_VARS_KEY + "', got: " + o);
        }

        Map<String, Object> r = (Map<String, Object>) o;
        if (r.size() != 1) {
            throw new IllegalArgumentException("Invalid reference format in '" + AnsibleConstants.GROUP_VARS_KEY + "', expected a single value, got: " + r);
        }

        Map.Entry<String, Object> e = r.entrySet().iterator().next();

        String groupName = e.getKey();

        Object v = e.getValue();
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Invalid reference value in '" + AnsibleConstants.GROUP_VARS_KEY + "', expected an object, got: " + v);
        }

        Map<String, Object> params = (Map<String, Object>) v;

        String orgName = (String) params.get("orgName");
        String secretName = (String) params.get("secretName");
        String password = (String) params.get("password");
        String type = (String) params.getOrDefault("type", "yml");

        return new Ref(groupName, orgName, secretName, password, type);
    }

    private static class Ref {

        private final String groupName;
        private final String orgName;
        private final String secretName;
        private final String password;
        private final String type;

        private Ref(String groupName, String orgName, String secretName, String password, String type) {
            this.groupName = groupName;
            this.orgName = orgName;
            this.secretName = secretName;
            this.password = password;
            this.type = type;
        }
    }
}
