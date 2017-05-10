package com.walmartlabs.concord.plugins.oneops;

import com.oneops.api.OOInstance;
import com.oneops.api.exception.OneOpsClientAPIException;
import com.oneops.api.resource.Transition;
import com.walmartlabs.concord.common.Task;
import io.takari.bpm.api.BpmnError;
import io.takari.bpm.api.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

@Named("oneOps")
public class OneOpsTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(OneOpsTask.class);
    private static final long DEFAULT_POLL_FREQUENCY = 3000;

    public static final String API_BASEURL = "api_baseurl";
    public static final String API_TOKEN = "api_token";
    public static final String ORGANIZATION_KEY = "organization";
    public static final String ASSEMBLY_KEY = "assembly";
    public static final String ENVIRONMENT_KEY = "environment";
    public static final String PLATFORM_KEY = "platform";
    public static final String COMPONENT_KEY = "component";
    public static final String VARIABLE_NAME_KEY = "variable_name";
    public static final String VARIABLE_VALUE_KEY = "variable_value";
    public static final String VARIABLE_NAMES_KEY = "variable_names";
    public static final String TO_UPPER_CASE_KEY = "to_upper_case";

    public void updateVariable(ExecutionContext ctx) {
        String org = (String) ctx.getVariable(ORGANIZATION_KEY);
        String asm = (String) ctx.getVariable(ASSEMBLY_KEY);
        String env = (String) ctx.getVariable(ENVIRONMENT_KEY);
        String platform = (String) ctx.getVariable(PLATFORM_KEY);
        String component = (String) ctx.getVariable(COMPONENT_KEY);
        String variableName = (String) ctx.getVariable(VARIABLE_NAME_KEY);
        String variableValue = (String) ctx.getVariable(VARIABLE_VALUE_KEY);

        if (variableName == null || variableName.isEmpty() || variableValue == null || variableValue.isEmpty()) {
            return;
        }

        try {
            OOInstance instance = connect(ctx);
            Transition t = new Transition(instance, asm);
            t.updatePlatformVariable(env, platform, variableName, variableValue, false);
        } catch (BpmnError e) {
            log.error("updateVariable ['{}', '{}', '{}', '{}', '{}', '{}', '{}'] -> error", org, asm, env, platform, component, variableName, variableValue, e);
            throw e;
        } catch (Exception e) {
            log.error("updateVariable ['{}', '{}', '{}', '{}', '{}', '{}', '{}'] -> error", org, asm, env, platform, component, variableName, variableValue, e);
            throw new BpmnError("updateVariableError", e);
        }
    }

    public void updateVariables(ExecutionContext ctx) {
        String org = (String) ctx.getVariable(ORGANIZATION_KEY);
        String asm = (String) ctx.getVariable(ASSEMBLY_KEY);
        String env = (String) ctx.getVariable(ENVIRONMENT_KEY);
        String platform = (String) ctx.getVariable(PLATFORM_KEY);
        String component = (String) ctx.getVariable(COMPONENT_KEY);
        boolean toUpperCase = Boolean.parseBoolean((String) ctx.getVariable(TO_UPPER_CASE_KEY));
        String[] keys = split((String) ctx.getVariable(VARIABLE_NAMES_KEY));

        OOInstance instance = connect(ctx);
        Transition t;
        try {
            t = new Transition(instance, asm);
        } catch (OneOpsClientAPIException e) {
            log.error("updateVariables ['{}', '{}', '{}', '{}', '{}'] -> error creating a transition", org, asm, env, platform, component, e);
            throw new BpmnError("updateVariableError", e);
        }

        for (String k : keys) {
            String varVal = (String) ctx.getVariable(k);
            String varName = toUpperCase ? k.toUpperCase() : k;
            try {
                t.updatePlatformVariable(env, platform, varName, varVal, false);
            } catch (BpmnError e) {
                log.error("updateVariables ['{}', '{}', '{}', '{}', '{}'] -> error updating the variable '{}'", org, asm, env, platform, component, varName, e);
                throw e;
            } catch (Exception e) {
                log.error("updateVariable ['{}', '{}', '{}', '{}', '{}'] -> error updating the variable '{}'", org, asm, env, platform, component, varName, e);
                throw new BpmnError("updateVariableError", e);
            }
        }
    }

    private String[] split(String s) {
        if (s == null) {
            return new String[0];
        }

        return s.split(",");
    }

    public void touchComponent(ExecutionContext ctx) {
        String org = (String) ctx.getVariable(ORGANIZATION_KEY);
        String asm = (String) ctx.getVariable(ASSEMBLY_KEY);
        String env = (String) ctx.getVariable(ENVIRONMENT_KEY);
        String platform = (String) ctx.getVariable(PLATFORM_KEY);
        String component = (String) ctx.getVariable(COMPONENT_KEY);

        try {
            OOInstance instance = connect(ctx);
            Transition t = new Transition(instance, asm);
            t.touchPlatformComponent(env, platform, component);
            log.info("touchComponent ['{}', '{}', '{}', '{}', '{}'] -> done", org, asm, env, platform, component);
        } catch (BpmnError e) {
            log.error("touchComponent ['{}', '{}', '{}', '{}', '{}'] -> error", org, asm, env, platform, component, e);
            throw e;
        } catch (Exception e) {
            log.error("touchComponent ['{}', '{}', '{}', '{}', '{}'] -> error", org, asm, env, platform, component, e);
            throw new BpmnError("touchComponentError", e);
        }
    }

    public void commitAndDeploy(ExecutionContext ctx) {
        String org = (String) ctx.getVariable(ORGANIZATION_KEY);
        String asm = (String) ctx.getVariable(ASSEMBLY_KEY);
        String env = (String) ctx.getVariable(ENVIRONMENT_KEY);
        String platform = (String) ctx.getVariable(PLATFORM_KEY);

        try {
            OOInstance instance = connect(ctx);
            Transition t = new Transition(instance, asm);
            t.commitEnvironment(env, null, "OneOpsTask");
            t.deploy(env, "OneOpsTask");
            log.info("commitAndDeploy ['{}', '{}', '{}', '{}'] -> done", org, asm, env, platform);
        } catch (BpmnError e) {
            log.error("commitAndDeploy ['{}', '{}', '{}', '{}'] -> error", org, asm, env, platform, e);
            throw e;
        } catch (Exception e) {
            log.error("commitAndDeploy ['{}', '{}', '{}', '{}'] -> error", org, asm, env, platform, e);
            throw new BpmnError("commitAndDeployError", e);
        }
    }

    private static OOInstance connect(ExecutionContext ctx) {
        OOInstance instance = new OOInstance();
        instance.setAuthtoken((String) ctx.getVariable(API_TOKEN));
        instance.setEndpoint((String) ctx.getVariable(API_BASEURL));
        instance.setOrgname((String) ctx.getVariable(ORGANIZATION_KEY));
        return instance;
    }
}
