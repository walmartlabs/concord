package com.walmartlabs.concord.plugins.bpmn.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "callActivity")
public class XmlCallActivity extends AbstractXmlElement {

    private static final long serialVersionUID = 1L;

    private String calledElement;

    @XmlAttribute
    public String getCalledElement() {
        return calledElement;
    }

    public void setCalledElement(String calledElement) {
        this.calledElement = calledElement;
    }
}
