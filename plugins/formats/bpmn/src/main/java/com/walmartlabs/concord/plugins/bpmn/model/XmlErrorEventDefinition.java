package com.walmartlabs.concord.plugins.bpmn.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;

@XmlRootElement(name = "errorEventDefinition")
public class XmlErrorEventDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    private String errorRef;

    @XmlAttribute
    public String getErrorRef() {
        return errorRef;
    }

    public void setErrorRef(String errorRef) {
        this.errorRef = errorRef;
    }
}
