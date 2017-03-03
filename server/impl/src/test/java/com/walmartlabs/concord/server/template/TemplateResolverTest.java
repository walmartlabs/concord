package com.walmartlabs.concord.server.template;

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class TemplateResolverTest extends AbstractDaoTest {

    @Test
    public void test() throws Exception {
        TemplateDao templateDao = new TemplateDao(getConfiguration(), mock(UserPermissionCleaner.class));
        TemplateResolver resolver = new TemplateResolver(templateDao);

        Path p = resolver.get("ansible");
        assertNotNull(p);
        assertTrue(Files.exists(p.resolve("_main.js")));
    }
}
