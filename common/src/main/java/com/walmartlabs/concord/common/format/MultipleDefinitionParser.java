package com.walmartlabs.concord.common.format;

import io.takari.bpm.model.ProcessDefinition;

import java.io.InputStream;
import java.util.Collection;

public interface MultipleDefinitionParser {

    /**
     * Parses a steam into a collection of process definitions.
     *
     * @param in
     * @return
     * @throws ParserException
     */
    Collection<ProcessDefinition> parse(InputStream in) throws ParserException;
}
