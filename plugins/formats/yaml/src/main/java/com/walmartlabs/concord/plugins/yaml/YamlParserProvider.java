package com.walmartlabs.concord.plugins.yaml;

import com.walmartlabs.concord.common.format.ParserException;
import com.walmartlabs.concord.common.format.PriorityBasedParser;
import io.takari.bpm.model.ProcessDefinition;

import javax.inject.Named;
import javax.inject.Provider;
import java.io.InputStream;
import java.util.Collection;

@Named
public class YamlParserProvider implements Provider<PriorityBasedParser> {

    private final YamlParser delegate = new YamlParser();

    @Override
    public PriorityBasedParser get() {
        return new PriorityBasedParser() {
            @Override
            public int getPriority() {
                return 200;
            }

            @Override
            public Collection<ProcessDefinition> parse(InputStream in) throws ParserException {
                return delegate.parse(in);
            }

            @Override
            public String toString() {
                return delegate.toString();
            }
        };
    }
}
