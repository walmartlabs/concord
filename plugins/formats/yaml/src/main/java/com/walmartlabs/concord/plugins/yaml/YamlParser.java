package com.walmartlabs.concord.plugins.yaml;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.walmartlabs.concord.common.format.ParserException;
import com.walmartlabs.concord.common.format.WorkflowDefinition;
import com.walmartlabs.concord.common.format.WorkflowDefinitionParser;
import io.takari.bpm.model.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Named
@Deprecated
public class YamlParser implements WorkflowDefinitionParser {

    private static final Logger log = LoggerFactory.getLogger(YamlParser.class);

    @Override
    public WorkflowDefinition parse(String source, InputStream in) throws ParserException {
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
        Collection<ProcessDefinition> pds = YamlConverter.convert(data);

        Map<String, ProcessDefinition> processes = new HashMap<>();
        for (ProcessDefinition pd : YamlConverter.convert(data)) {
            processes.put(pd.getId(), pd);
        }

        return new WorkflowDefinition(source, processes, Collections.emptyMap());
    }

    @Override
    public String toString() {
        return "Concord YAML parser";
    }
}
