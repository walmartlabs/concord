package com.walmartlabs.concord.project.yaml.converter;

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


import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlFormCall;
import io.takari.bpm.model.UserTask;
import io.takari.bpm.model.form.FormExtension;

import java.util.*;
import java.util.stream.Collectors;

public class YamlFormCallConverter implements StepConverter<YamlFormCall> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlFormCall s) throws YamlConverterException {
        Chunk c = new Chunk();

        String id = ctx.nextId();
        Map<String, Object> opts = (Map<String, Object>) StepConverter.deepConvert(s.getOptions());
        if (opts != null && opts.isEmpty()) {
            opts = null;
        }

        if (opts != null && opts.get(InternalConstants.Forms.RUN_AS_KEY) != null) {
            Map<String, Object> runAsParams = (Map<String, Object>) opts.get(InternalConstants.Forms.RUN_AS_KEY);
            Object ldapObj = runAsParams.get(InternalConstants.Forms.RUN_AS_LDAP_KEY);

            runAsParams.put(InternalConstants.Forms.RUN_AS_LDAP_KEY, getGroupsMap(ldapObj));
        }

        c.addElement(new UserTask(id, new FormExtension(s.getKey(), opts)));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Form: " + s.getKey()));

        return c;
    }

    private Map<String, Object> getGroupsMap(Object ldapObj) {
        List<String> groupList = null;
        if (ldapObj instanceof List) {
            groupList = ((List<Map>) ldapObj).stream()
                    .map(group -> (String) group.get(InternalConstants.Forms.RUN_AS_GROUP_KEY))
                    .collect(Collectors.toList());
        } else if (ldapObj instanceof Map) {
            groupList = new ArrayList<>();
            groupList.add((String) ((Map) ldapObj).get(InternalConstants.Forms.RUN_AS_GROUP_KEY));
        }

        return Collections.singletonMap(InternalConstants.Forms.RUN_AS_GROUP_KEY, groupList);
    }
}
