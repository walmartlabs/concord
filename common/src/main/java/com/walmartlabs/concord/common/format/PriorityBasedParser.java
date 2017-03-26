package com.walmartlabs.concord.common.format;

public interface PriorityBasedParser extends WorkflowDefinitionParser {

    int getPriority();
}
