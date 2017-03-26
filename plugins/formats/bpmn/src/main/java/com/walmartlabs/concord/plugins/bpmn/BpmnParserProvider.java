package com.walmartlabs.concord.plugins.bpmn;

import com.walmartlabs.concord.common.format.ParserException;
import com.walmartlabs.concord.common.format.PriorityBasedParser;
import com.walmartlabs.concord.common.format.WorkflowDefinition;

import javax.inject.Named;
import javax.inject.Provider;
import java.io.InputStream;

@Named
public class BpmnParserProvider implements Provider<PriorityBasedParser> {

    private final BpmnParser delegate = new BpmnParser();

    @Override
    public PriorityBasedParser get() {
        return new PriorityBasedParser() {
            @Override
            public int getPriority() {
                return 100;
            }

            @Override
            public WorkflowDefinition parse(String source, InputStream in) throws ParserException {
                return delegate.parse(source, in);
            }

            @Override
            public String toString() {
                return delegate.toString();
            }
        };
    }
}
