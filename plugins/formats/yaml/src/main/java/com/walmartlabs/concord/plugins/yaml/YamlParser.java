package com.walmartlabs.concord.plugins.yaml;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.walmartlabs.concord.common.format.MultipleDefinitionParser;
import com.walmartlabs.concord.common.format.ParserException;
import io.takari.bpm.model.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

@Named
@Deprecated
public class YamlParser implements MultipleDefinitionParser {

    private static final Logger log = LoggerFactory.getLogger(YamlParser.class);

    @Override
    public Collection<ProcessDefinition> parse(InputStream in) throws ParserException {
        log.warn("parse -> this format is deprecated");

        YAMLMapper m = new YAMLMapper();

        Map<String, Object> data;
        try {
            data = m.readValue(in, Map.class);
        } catch (IOException e) {
            throw new ParserException("Error while parsing the document", e);
        }

        if (data == null || data.isEmpty()) {
            throw new ParserException("No definitions found");
        }

        log.debug("parse -> got: {}", data);
        return YamlConverter.convert(data);
    }

    @Override
    public String toString() {
        return "Concord YAML parser";
    }
}
