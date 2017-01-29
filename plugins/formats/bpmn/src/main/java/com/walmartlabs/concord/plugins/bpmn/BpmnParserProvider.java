package com.walmartlabs.concord.plugins.bpmn;

import com.walmartlabs.concord.common.format.ParserException;
import com.walmartlabs.concord.common.format.PriorityBasedParser;
import io.takari.bpm.model.ProcessDefinition;

import javax.inject.Named;
import javax.inject.Provider;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

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
            public Collection<ProcessDefinition> parse(InputStream in) throws ParserException {
                try {
                    return Collections.singleton(delegate.parse(in));
                } catch (io.takari.bpm.xml.ParserException e) {
                    throw new ParserException("Error while parsing a BPMN file", e);
                }
            }

            @Override
            public String toString() {
                return delegate.toString();
            }
        };
    }
}
