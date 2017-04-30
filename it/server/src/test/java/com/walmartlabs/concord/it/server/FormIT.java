package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.process.*;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FormIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        String firstName = "john_" + System.currentTimeMillis();
        String lastName = "smith_" + System.currentTimeMillis();
        byte[] payload = archive(FormIT.class.getResource("form").toURI());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload));

        waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.SUSPENDED);

        // ---

        FormResource formResource = proxy(FormResource.class);

        List<FormListEntry> forms = formResource.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        String formId = forms.get(0).getFormInstanceId();

        Map<String, Object> data = Collections.singletonMap("firstName", firstName);
        FormSubmitResponse fsr = formResource.submit(spr.getInstanceId(), formId, data);
        assertTrue(fsr.isOk());

        ProcessStatusResponse psr = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.SUSPENDED);

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*100223.*", ab);

        // ---

        forms = formResource.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        formId = forms.get(0).getFormInstanceId();

        data = Collections.singletonMap("lastName", lastName);
        fsr = formResource.submit(spr.getInstanceId(), formId, data);
        assertTrue(fsr.isOk());

        psr = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, psr.getStatus());

        // ---

        ab = getLog(psr.getLogFileName());
        assertLog(".*" + firstName + " " + lastName + ".*", ab);
        assertLog(".*100323.*", ab);
        assertLog(".*red.*", ab);
    }
}
