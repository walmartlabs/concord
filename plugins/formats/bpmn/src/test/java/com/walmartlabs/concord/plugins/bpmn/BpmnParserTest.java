package com.walmartlabs.concord.plugins.bpmn;

import io.takari.bpm.model.*;
import io.takari.bpm.xml.Parser;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.*;

public class BpmnParserTest {
    
    @Test
    public void testSimple() throws Exception {
        InputStream in = ClassLoader.getSystemResourceAsStream("camunda.bpmn");
        Parser p = new BpmnParser();

        ProcessDefinition pd = p.parse(in);
        assertNotNull(pd);
        assertEquals("Process_1", pd.getId());
    }
    
    @Test
    public void testComplex() throws Exception {
        InputStream in = ClassLoader.getSystemResourceAsStream("complex.bpmn");
        Parser p = new BpmnParser();

        ProcessDefinition pd = p.parse(in);
        assertNotNull(pd);
        assertNotNull(pd.getChildren());
        assertEquals(21, pd.getChildren().size());
    }

    @Test
    public void testConcord() throws Exception {
        InputStream in = ClassLoader.getSystemResourceAsStream("concord.bpmn");
        Parser p = new BpmnParser();

        ProcessDefinition pd = p.parse(in);
        assertNotNull(pd);
        assertNotNull(pd.getChildren());
        assertEquals(30, pd.getChildren().size());

        // ---

        AbstractElement e = pd.getChild("timeDurationExample");
        assertNotNull(e);
        assertTrue(e instanceof IntermediateCatchEvent);

        IntermediateCatchEvent ev1 = (IntermediateCatchEvent) e;
        assertEquals("PT35S", ev1.getTimeDuration());

        // ---

        e = pd.getChild("defaultFlowExample");
        assertNotNull(e);
        assertTrue(e instanceof ExclusiveGateway);

        ExclusiveGateway gw = (ExclusiveGateway) e;
        assertEquals("defaultFlowId", gw.getDefaultFlow());

        // ---

        e = pd.getChild("callActivityExample");
        assertNotNull(e);
        assertTrue(e instanceof CallActivity);

        CallActivity a = (CallActivity) e;
        assertEquals("testActivity", a.getCalledElement());

        // ---

        e = pd.getChild("boundaryErrorExample");
        assertNotNull(e);
        assertTrue(e instanceof BoundaryEvent);

        BoundaryEvent ev2 = (BoundaryEvent) e;
        assertEquals("myErrorCode", ev2.getErrorRef());
    }
}
