package com.walmartlabs.concord.plugins.mock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.runtime.v2.model.Location;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Named("verify")
//TODO: @DryRunReady
public class VerifyTask implements Task {

    private final ObjectMapper objectMapper;
    private final Invocations invocations;
    private final Location verifyStepLocation;

    @Inject
    public VerifyTask(ObjectMapper objectMapper, Invocations invocations, Context context) {
        this.objectMapper = objectMapper;
        this.invocations = invocations;

        var currentStep = context.execution().currentStep();
        this.verifyStepLocation = currentStep != null ? currentStep.getLocation() : null;
    }

    public Verifier task(String taskName, int count) {
        return new Verifier(taskName, count);
    }

    public class Verifier {

        private static final Logger log = LoggerFactory.getLogger("processLog");

        private final String taskName;
        private final int count;

        public Verifier(String taskName, int count) {
            this.taskName = taskName;
            this.count = count;
        }

        public void verify(String methodName, Object[] args) {
            List<Invocation> result = invocations.find(taskName, methodName, args);
            if (result.size() != count) {
                dumpInvocations(result, taskName, methodName, args, count);

                throw new UserDefinedException("verify failed");
            }
        }

        private void dumpInvocations(List<Invocation> matchedInvocations, String expectedTaskName, String expectedMethodName, Object[] args, int expectedCount) {
            StringBuilder logMessage = new StringBuilder();

            String invocation = String.format("%s.%s(%s) @ %s",
                    expectedTaskName, expectedMethodName,
                    argsToString(args), locationToString(verifyStepLocation));

            if (matchedInvocations.isEmpty()) {
                logMessage.append("Wanted but not invoked:\n")
                        .append(invocation).append('\n');

                appendTaskInteractions(logMessage, expectedMethodName);
            } else {
                logMessage.append("Wanted ").append(expectedCount).append(pluralize(" time", expectedCount))
                        .append(":\n")
                        .append(invocation).append('\n');

                appendMatchedInvocations(logMessage, matchedInvocations);
            }

            log.info("\n{}", logMessage);
        }

        private void appendTaskInteractions(StringBuilder logMessage, String expectedMethodName) {
            List<Invocation> taskInvocations = invocations.find(taskName, expectedMethodName);
            if (!taskInvocations.isEmpty()) {
                logMessage.append("However, there was ")
                        .append(taskInvocations.size()).append(pluralize(" interaction", taskInvocations.size()))
                        .append(" with this task:\n");

                taskInvocations.forEach(invocation ->
                        logMessage.append("-> ").append(invocationToString(invocation)).append('\n'));
            }
        }

        private void appendMatchedInvocations(StringBuilder logMessage, List<Invocation> matchedInvocations) {
            if (!matchedInvocations.isEmpty()) {
                logMessage.append("But was ").append(matchedInvocations.size()).append(pluralize(" time", matchedInvocations.size()))
                        .append(":\n");

                matchedInvocations.forEach(invocation ->
                        logMessage.append("-> ").append(invocationToString(invocation)).append('\n'));
            } else {
                logMessage.append("But no interactions recorded\n");
            }
        }

        private String invocationToString(Invocation invocation) {
            return String.format("%s:%d => %s.%s(%s)", invocation.fileName(), invocation.line(),
                    invocation.taskName(), invocation.methodName(), argsToString(invocation.args()));
        }

        private static String pluralize(String word, int count) {
            return word + (count > 1 ? "s" : "");
        }

        private String argsToString(Object[] args) {
            if (args == null || args.length == 0) {
                return "";
            }

            return argsToString(Arrays.asList(args));
        }

        private String argsToString(List<Object> args) {
            if (args.isEmpty()) {
                return "";
            }

            return args.stream()
                    .map(arg -> {
                        try {
                            return objectMapper.writeValueAsString(arg);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException("Can't serialize argument: " + arg, e);
                        }
                    })
                    .collect(Collectors.joining(", "));
        }

        private static String locationToString(Location location) {
            if (location == null || location.fileName() == null) {
                return "";
            }

            return location.fileName() + ":" + location.lineNum();
        }
    }
}
