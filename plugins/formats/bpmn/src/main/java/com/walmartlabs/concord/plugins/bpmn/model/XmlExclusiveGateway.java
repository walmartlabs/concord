package com.walmartlabs.concord.plugins.bpmn.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "exclusiveGateway")
public class XmlExclusiveGateway extends AbstractXmlElement {

    private static final long serialVersionUID = 1L;

    private String defaultFlow;

    @XmlAttribute(name = "default")
    public String getDefaultFlow() {
        return defaultFlow;
    }

    public void setDefaultFlow(String defaultFlow) {
        this.defaultFlow = defaultFlow;
    }
}
