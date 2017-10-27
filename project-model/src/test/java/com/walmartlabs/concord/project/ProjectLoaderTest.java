package com.walmartlabs.concord.project;

import com.walmartlabs.concord.project.model.ProjectDefinition;
import org.junit.Test;

import java.net.URI;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ProjectLoaderTest {

    @Test
    public void testSimple() throws Exception {
        ProjectLoader loader = new ProjectLoader();

        URI uri = ClassLoader.getSystemResource("simple").toURI();
        ProjectDefinition pd = loader.load(Paths.get(uri));

        assertNotNull(pd);

        assertNotNull(pd.getFlows().get("main"));
        assertNotNull(pd.getFlows().get("other"));

        assertNotNull(pd.getForms().get("myForm"));
    }

    @Test
    public void testComplex() throws Exception {
        ProjectLoader loader = new ProjectLoader();

        URI uri = ClassLoader.getSystemResource("complex").toURI();
        ProjectDefinition pd = loader.load(Paths.get(uri));
        assertNotNull(pd);

        assertNotNull(pd.getTriggers());
        assertEquals(2, pd.getTriggers().size());
    }
}
