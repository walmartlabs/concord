package com.walmartlabs.concord.common.format;

import java.io.InputStream;

public interface WorkflowDefinitionParser {

    WorkflowDefinition parse(String source, InputStream in) throws ParserException;
}
