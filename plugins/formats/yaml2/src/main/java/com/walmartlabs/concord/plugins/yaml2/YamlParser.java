package com.walmartlabs.concord.plugins.yaml2;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import com.walmartlabs.concord.common.format.ParserException;
import com.walmartlabs.concord.common.format.WorkflowDefinition;
import com.walmartlabs.concord.common.format.WorkflowDefinitionParser;
import com.walmartlabs.concord.plugins.yaml2.model.YamlDefinition;
import com.walmartlabs.concord.plugins.yaml2.model.YamlFormDefinition;
import com.walmartlabs.concord.plugins.yaml2.model.YamlProcessDefinition;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.parc.Input;
import io.takari.parc.Result;
import io.takari.parc.Seq;

import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named
public class YamlParser implements WorkflowDefinitionParser {

    @Override
    public WorkflowDefinition parse(String source, InputStream in) throws ParserException {
        YAMLFactory f = new YAMLFactory();
        try {
            YAMLParser p = f.createParser(in);
            Input<Atom> i = toInput(p);

            Result<?, Seq<YamlDefinition>> result = YamlGrammar.getParser().parse(i);
            if (result.isFailure()) {
                throw new ParserException("Parsing error: [" + source + "] " + result);
            }

            Seq<YamlDefinition> defs = result.toSuccess().getResult();
            return convert(source, defs.toList());
        } catch (YamlConverterException e) {
            throw new ParserException("Error while converting a document", e);
        } catch (IOException e) {
            throw new ParserException("Error while parsing a document", e);
        }
    }

    private static Input<Atom> toInput(YAMLParser p) throws IOException {
        List<Atom> atoms = new ArrayList<>();

        while (p.nextToken() != null) {
            atoms.add(Atom.current(p));
        }

        return Input.of(atoms.toArray(new Atom[atoms.size()]));
    }

    private static WorkflowDefinition convert(String source, List<YamlDefinition> defs) throws YamlConverterException {
        Map<String, ProcessDefinition> processes = new HashMap<>();
        Map<String, FormDefinition> forms = new HashMap<>();

        for (YamlDefinition d : defs) {
            if (d instanceof YamlProcessDefinition) {
                ProcessDefinition pd = YamlConverter.convert((YamlProcessDefinition) d);
                processes.put(pd.getId(), pd);
            } else if (d instanceof YamlFormDefinition) {
                FormDefinition fd = YamlConverter.convert((YamlFormDefinition) d);
                forms.put(fd.getName(), fd);
            } else {
                throw new YamlConverterException("Unsupported definition: " + d);
            }
        }

        return new WorkflowDefinition(source, processes, forms);
    }

    @Override
    public String toString() {
        return "Concord YAML parser (v2)";
    }
}
