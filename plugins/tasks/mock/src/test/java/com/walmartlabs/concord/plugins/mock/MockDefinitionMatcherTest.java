package com.walmartlabs.concord.plugins.mock;

import com.walmartlabs.concord.runtime.v2.model.Location;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.model.TaskCallOptions;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.plugins.mock.MockDefinitionProvider.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class MockDefinitionMatcherTest {

    private MockDefinitionMatcher mockDefinitionMatcher;

    @BeforeEach
    public void setUp() {
        mockDefinitionMatcher = new MockDefinitionMatcher();
    }

    @Test
    public void testTaskMatch() {
        /*
         task: myTask
         in:
           param1: value1
           param2: value2
         */
        var context = MockDefinitionContext.task(mock(Step.class), "myTask", new MapBackedVariables(Map.of("param1", "value1", "param2", "value2")));
        var mock = new MockDefinition(Map.of(
                "task", "myTask",
                "in", Map.of("param1", "value1")
        ));

        assertTrue(mockDefinitionMatcher.matches(context, mock));
    }

    @Test
    public void testMatchOnlyByMeta() {
        /*
         task: myTask
         in:
           param1: value1
           param2: value2
         */
        var currentStep = new TaskCall(Location.builder().build(), "myTask", TaskCallOptions.builder().meta(Map.of("taskId", "BOO")).build());
        var context = MockDefinitionContext.task(currentStep, "myTask", new MapBackedVariables(Map.of("param1", "value1", "param2", "value2")));
        var mock = new MockDefinition(Map.of(
                "task", "myTask",
                "stepMeta", Map.of("taskId", "BO.*")
        ));

        assertTrue(mockDefinitionMatcher.matches(context, mock));
    }

    @Test
    public void testTaskMatchByMeta() {
        /*
         task: myTask
         in:
           param1: value1
         meta:
           taskId: "BOO"
         */
        var currentStep = new TaskCall(Location.builder().build(), "myTask", TaskCallOptions.builder().meta(Map.of("taskId", "BOO")).build());
        var context = MockDefinitionContext.task(currentStep, "myTask", new MapBackedVariables(Map.of("param1", "value1", "param2", "value2")));
        var mock = new MockDefinition(Map.of(
                "task", "myTask",
                "in", Map.of("param1", "value1"),
                "stepMeta", Map.of("taskId", "BO.*")
        ));

        assertTrue(mockDefinitionMatcher.matches(context, mock));
    }

    @Test
    public void testTaskMethodMatch() {
        // expr: ${myTask.myMethod(1, 2)}
        var context = MockDefinitionContext.method(mock(Step.class), "myTask", "myMethod", new Object[] {1, 2});

        var mock = new MockDefinition(Map.of(
                "task", "myTask",
                "method", "myMethod",
                "args", List.of(1, 2)
        ));

        assertTrue(mockDefinitionMatcher.matches(context, mock));
    }

    @Test
    public void testTaskMethodMatchByMeta() {
        // expr: ${myTask.myMethod(1, 2)}
        // meta:
        //   taskId: "BOO"
        var currentStep = new TaskCall(Location.builder().build(), "myTask", TaskCallOptions.builder().meta(Map.of("taskId", "BOO")).build());
        var context = MockDefinitionContext.method(currentStep, "myTask", "myMethod", new Object[] {1, 2});

        var mock = new MockDefinition(Map.of(
                "task", "myTask",
                "method", "myMethod",
                "args", List.of(1, 2),
                "stepMeta", Map.of("taskId", "BO.*")
        ));

        assertTrue(mockDefinitionMatcher.matches(context, mock));
    }

    @Test
    public void testNotMatch_taskName() {
        /*
         task: myTask
         in:
           param1: value1
           param2: value2
         */
        var context = MockDefinitionContext.task(mock(Step.class), "myTask", new MapBackedVariables(Map.of("param1", "value1", "param2", "value2")));
        var mock = new MockDefinition(Map.of(
                "task", "myTask2",
                "in", Map.of("param1", "value1")
        ));

        // real taskName=myTask, mocked: "myTask2"
        assertFalse(mockDefinitionMatcher.matches(context, mock));
    }

    @Test
    public void testNotMatch_InputParams() {
        /*
         task: myTask
         in:
           param1: value1
           param2: value2
         */
        var context = MockDefinitionContext.task(mock(Step.class), "myTask", new MapBackedVariables(Map.of("param1", "value1", "param2", "value2")));
        var mock = new MockDefinition(Map.of(
                "task", "myTask2",
                "in", Map.of("param3", "value3")
        ));

        assertFalse(mockDefinitionMatcher.matches(context, mock));
    }

    @Test
    public void testNotMatch_Meta() {
        /*
         task: myTask
         in:
           param1: value1
           param2: value2
         */
        var context = MockDefinitionContext.task(mock(Step.class), "myTask", new MapBackedVariables(Map.of("param1", "value1", "param2", "value2")));
        var mock = new MockDefinition(Map.of(
                "task", "myTask2",
                "stepMeta", Map.of("taskId", "BO.*")
        ));

        assertFalse(mockDefinitionMatcher.matches(context, mock));
    }
}
