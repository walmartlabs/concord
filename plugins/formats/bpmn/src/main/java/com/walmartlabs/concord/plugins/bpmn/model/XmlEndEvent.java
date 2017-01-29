package com.walmartlabs.concord.plugins.bpmn.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "endEvent")
public class XmlEndEvent extends AbstractXmlElement {

    private static final long serialVersionUID = 1L;

    private XmlErrorEventDefinition errorEventDefinition;

    public XmlErrorEventDefinition getErrorEventDefinition() {
        return errorEventDefinition;
    }

    public void setErrorEventDefinition(XmlErrorEventDefinition errorEventDefinition) {
        this.errorEventDefinition = errorEventDefinition;
    }
}
