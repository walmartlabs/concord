package com.walmartlabs.concord.plugins.bpmn.model;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "process")
public class XmlProcess extends AbstractXmlElement {

    private static final long serialVersionUID = 1L;

    private List<AbstractXmlElement> elements = new ArrayList<>();

    @XmlElementRef
    public List<AbstractXmlElement> getElements() {
        return elements;
    }

    public void setElements(List<AbstractXmlElement> elements) {
        this.elements = elements;
    }
}
