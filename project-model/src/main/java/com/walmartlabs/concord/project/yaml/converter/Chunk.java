package com.walmartlabs.concord.project.yaml.converter;

import io.takari.bpm.model.AbstractElement;
import io.takari.bpm.model.SourceMap;

import java.io.Serializable;
import java.util.*;

public class Chunk implements Serializable {

    private final List<AbstractElement> elements = new ArrayList<>();
    private final Set<String> outputs = new HashSet<>();
    private final Map<String, SourceMap> sourceMap = new HashMap<>();

    public void addElement(AbstractElement e) {
        elements.add(e);
    }

    public void addElements(List<AbstractElement> l) {
        elements.addAll(l);
    }

    public void addOutput(String id) {
        outputs.add(id);
    }

    public void addOutputs(Set<String> s) {
        outputs.addAll(s);
    }

    public void addSourceMap(String id, SourceMap s) {
        sourceMap.put(id, s);
    }

    public void addSourceMaps(Map<String, SourceMap> m) {
        sourceMap.putAll(m);
    }

    public AbstractElement firstElement() {
        return elements.get(0);
    }

    public List<AbstractElement> getElements() {
        return elements;
    }

    public Set<String> getOutputs() {
        return outputs;
    }

    public Map<String, SourceMap> getSourceMap() {
        return sourceMap;
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }
}
