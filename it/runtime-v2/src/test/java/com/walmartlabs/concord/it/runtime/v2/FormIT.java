package com.walmartlabs.concord.it.runtime.v2;

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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit4.ConcordRule;
import com.walmartlabs.concord.client.FormListEntry;
import com.walmartlabs.concord.client.FormSubmitResponse;
import com.walmartlabs.concord.client.ProcessEntry;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static com.walmartlabs.concord.it.runtime.v2.ITConstants.DEFAULT_TEST_TIMEOUT;
import static org.junit.Assert.*;

public class FormIT {

    @Rule
    public final ConcordRule concord = ConcordConfiguration.configure();

    /**
     * A straightforward single form process.
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void test() throws Exception {
        Payload payload = new Payload()
                .archive(FormIT.class.getResource("form").toURI());

        // ---

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.SUSPENDED);
        assertEquals(ProcessEntry.StatusEnum.SUSPENDED, pe.getStatus());

        // ---

        List<FormListEntry> forms = proc.forms();
        assertEquals(1, forms.size());

        // ---

        FormListEntry myForm = forms.get(0);
        assertFalse(myForm.isCustom());

        String formName = myForm.getName();

        String firstName = "john_" + randomString();
        String lastName = "smith_" + randomString();
        int age = ThreadLocalRandom.current().nextInt(100);

        Map<String, Object> data = new HashMap<>();
        data.put("lastName", lastName);
        data.put("firstName", firstName);
        data.put("age", age);

        FormSubmitResponse fsr = proc.submitForm(formName, data);
        assertTrue(fsr.isOk());
        assertTrue(fsr.getErrors() == null || fsr.getErrors().isEmpty());

        pe = proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        proc.assertLog(".*firstName=" + firstName + ".*");
        proc.assertLog(".*lastName=" + lastName + ".*");
        proc.assertLog(".*age=" + age + ".*");
    }

    /**
     * Start a process with a form and a sleep task. Cancel the process while sleeping
     * and check the onCancel process for variables. We expect the submitted values
     * to be available in the onCancel flow.
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testFormOnCancel() throws Exception {
        Payload payload = new Payload()
                .archive(FormIT.class.getResource("formOnCancel").toURI());

        // ---

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.SUSPENDED);
        assertEquals(ProcessEntry.StatusEnum.SUSPENDED, pe.getStatus());

        List<FormListEntry> forms = proc.forms();
        assertEquals(1, forms.size());

        FormListEntry myForm = forms.get(0);

        String firstName = "john_" + randomString();
        int age = ThreadLocalRandom.current().nextInt(100);

        Map<String, Object> data = new HashMap<>();
        data.put("firstName", firstName);
        data.put("age", age);

        FormSubmitResponse fsr = proc.submitForm(myForm.getName(), data);
        assertTrue(fsr.isOk());

        // ---

        proc.waitForStatus(ProcessEntry.StatusEnum.RUNNING);
        proc.kill();
        proc.waitForStatus(ProcessEntry.StatusEnum.CANCELLED);

        ConcordProcess child = concord.processes().get(proc.waitForChildStatus(ProcessEntry.StatusEnum.FINISHED).getInstanceId());
        child.assertLog(".*myForm.firstName: " + firstName + ".*");
        child.assertLog(".*myForm.age: " + age + ".*");
    }
}
