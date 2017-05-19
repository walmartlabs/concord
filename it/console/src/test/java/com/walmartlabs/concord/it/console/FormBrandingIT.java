package com.walmartlabs.concord.it.console;

import com.walmartlabs.concord.server.api.process.*;
import com.walmartlabs.concord.server.console.CustomFormService;
import com.walmartlabs.concord.server.console.FormSessionResponse;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;
import static org.junit.Assert.*;

// TODO make it work with a local webpack server
// TODO make it work with auth
public class FormBrandingIT extends AbstractIT {

    @Test(timeout = 30000)
    public void testBranding() throws Exception {
        byte[] payload = archive(FormBrandingIT.class.getResource("formBranding").toURI());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), false);

        waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.SUSPENDED);

        // ---

        FormResource formResource = proxy(FormResource.class);

        List<FormListEntry> forms = formResource.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        FormListEntry form = forms.get(0);
        assertTrue(form.isCustom());

        CustomFormService customFormService = proxy(CustomFormService.class);
        FormSessionResponse fsr = customFormService.startSession(spr.getInstanceId(), form.getFormInstanceId());
        assertNotNull(fsr);

        // ---

        /*
        getDriver().navigate().to("http://localhost:8080" + fsr.getUri());
        Alert alert = waitForAlert("Login");
        alert.authenticateUsing(new UserAndPassword("user", "pwd"));

        // ---

        WebElement firstName = waitFor("First Name", By.name("firstName"));
        firstName.clear();
        firstName.sendKeys("John");

        WebElement lastName = waitFor("Last Name", By.name("lastName"));
        lastName.clear();
        lastName.sendKeys("Smith");

        WebElement submitButton = waitFor("Submit button", By.cssSelector("[type=submit]"));

        // TODO auth
        // submitButton.click();
        */
    }
}
