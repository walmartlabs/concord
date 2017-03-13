package com.walmartlabs.concord.plugins.yaml2;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import com.walmartlabs.concord.common.format.MultipleDefinitionParser;
import com.walmartlabs.concord.common.format.ParserException;
import com.walmartlabs.concord.plugins.yaml2.model.YamlDefinition;
import com.walmartlabs.concord.plugins.yaml2.model.YamlProcessDefinition;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.parc.Input;
import io.takari.parc.Result;
import io.takari.parc.Seq;

import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Named
public class YamlParser implements MultipleDefinitionParser {

    @Override
    public Collection<ProcessDefinition> parse(InputStream in) throws ParserException {
        YAMLFactory f = new YAMLFactory();
        try {
            YAMLParser p = f.createParser(in);
            Input<Atom> i = toInput(p);

            Result<?, Seq<YamlDefinition>> result = YamlGrammar.getParser().parse(i);
            if (result.isFailure()) {
                throw new ParserException("Parsing error: " + result);
            }

            Seq<YamlDefinition> defs = result.toSuccess().getResult();
            return convert(defs.toList());
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

    private static Collection<ProcessDefinition> convert(List<YamlDefinition> defs) throws YamlConverterException {
        Collection<ProcessDefinition> result = new ArrayList<>();

        for (YamlDefinition d : defs) {
            if (d instanceof YamlProcessDefinition) {
                ProcessDefinition pd = convert((YamlProcessDefinition) d);
                result.add(pd);
            }
        }

        // TODO forms

        return result;
    }

    private static ProcessDefinition convert(YamlProcessDefinition def) throws YamlConverterException {
        return YamlConverter.convert(def);
    }

    @Override
    public String toString() {
        return "Concord YAML parser (v2)";
    }
}
