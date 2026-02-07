package com.walmartlabs.concord.it.runtime.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.walmartlabs.concord.client2.FormListEntry;
import com.walmartlabs.concord.client2.FormSubmitResponse;
import com.walmartlabs.concord.client2.ProcessEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static org.junit.jupiter.api.Assertions.*;

public class ProfilesIT extends AbstractTest {

    static ConcordRule concord;

    @BeforeAll
    static void setUp(ConcordRule rule) {
        concord = rule;
    }

    /**
     * Override flows from active profiles.
     */
    @Test
    public void testFlowOverride() throws Exception {
        Payload payload = new Payload()
                .activeProfiles("stranger")
                .archive(resource("profileFlow"));

        ConcordProcess proc = concord.processes().start(payload);

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*From profile: Hello, stranger.*");
    }

    /**
     * Override/define forms from profiles.
     */
    @Test
    public void testFormOverride() throws Exception {
        Payload payload = new Payload()
                .activeProfiles("stranger")
                .archive(resource("profileForm"));

        ConcordProcess proc = concord.processes().start(payload);

        // ---

        expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        List<FormListEntry> forms = proc.forms();
        assertEquals(1, forms.size());

        FormListEntry myForm = forms.get(0);
        assertNotNull(myForm);

        String firstName = "john_" + randomString();
        String lastName = "smith_" + randomString();
        Map<String, Object> data = new HashMap<>();
        data.put("lastName", lastName);
        data.put("firstName", firstName);

        FormSubmitResponse fsr = proc.submitForm(myForm.getName(), data);
        assertTrue(fsr.getOk());
        assertTrue(fsr.getErrors() == null || fsr.getErrors().isEmpty());

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*firstName=" + firstName + ".*");
        proc.assertLog(".*lastName=" + lastName + ".*");
    }
}
