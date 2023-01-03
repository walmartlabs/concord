package com.walmartlabs.concord.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.plugins.ansible.secrets.AnsibleSecretService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.sdk.MapUtils.assertString;
import static com.walmartlabs.concord.sdk.MapUtils.getString;

public class GroupVarsProcessor {

    private final Logger log = LoggerFactory.getLogger(GroupVarsProcessor.class);

    private final AnsibleSecretService secretService;

    private final Collection<Path> exportedFiles = new HashSet<>();

    public GroupVarsProcessor(AnsibleSecretService secretService) {
        this.secretService = secretService;
    }

    public void process(AnsibleContext context, String playbook) throws Exception {
        Collection<Ref> refs = toRefs(context.args());
        if (refs == null) {
            return;
        }

        Path playbookPath = context.workDir().resolve(playbook);

        Path groupVarsBase = playbookPath.getParent().resolve("group_vars");
        if (Files.notExists(groupVarsBase)) {
            Files.createDirectories(groupVarsBase);
        }

        for (Ref r : refs) {
            export(r, groupVarsBase);
        }
    }

    public void postProcess() throws Exception {
        for (Path p : exportedFiles) {
            Files.deleteIfExists(p);
            log.info("Removed exported file {}", p);
        }
    }

    private void export(Ref r, Path groupVarsBase) throws Exception {
        Path src = secretService.exportAsFile(r.orgName, r.secretName, r.password);

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
        Object v = args.get(TaskParams.GROUP_VARS_KEY.getKey());
        if (v == null) {
            return null;
        }

        if (!(v instanceof Collection)) {
            throw new IllegalArgumentException("'" + TaskParams.GROUP_VARS_KEY.getKey() + "' must be a list of group_vars references");
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
            throw new IllegalArgumentException("Unsupported object type in '" + TaskParams.GROUP_VARS_KEY.getKey() + "', got: " + o);
        }

        Map<String, Object> r = (Map<String, Object>) o;
        if (r.size() != 1) {
            throw new IllegalArgumentException("Invalid reference format in '" + TaskParams.GROUP_VARS_KEY.getKey() + "', expected a single value, got: " + r);
        }

        Map.Entry<String, Object> e = r.entrySet().iterator().next();

        String groupName = e.getKey();

        Object v = e.getValue();
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Invalid reference value in '" + TaskParams.GROUP_VARS_KEY.getKey() + "', expected an object, got: " + v);
        }

        Map<String, Object> params = (Map<String, Object>) v;

        String orgName = getString(params, "orgName", null);
        orgName = getString(params, "org", orgName); // alternative parameter name

        String secretName = assertString(params, "secretName");

        String password = getString(params, "password", null);
        String type = getString(params, "type", "yml");

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
