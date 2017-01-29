package com.walmartlabs.concord.plugins.bpmn.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "boundaryEvent")
public class XmlBoundaryEvent extends AbstractXmlElement {

    private static final long serialVersionUID = 1L;

    private String attachedToRef;
    private XmlErrorEventDefinition errorEventDefinition;

    @XmlAttribute
    public String getAttachedToRef() {
        return attachedToRef;
    }

    public void setAttachedToRef(String attachedToRef) {
        this.attachedToRef = attachedToRef;
    }

    public XmlErrorEventDefinition getErrorEventDefinition() {
        return errorEventDefinition;
    }

    public void setErrorEventDefinition(XmlErrorEventDefinition errorEventDefinition) {
        this.errorEventDefinition = errorEventDefinition;
    }
}
