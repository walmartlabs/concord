package com.walmartlabs.concord.plugins.bpmn.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.Collection;

@XmlRootElement(name = "definitions")
public class XmlDefinitions implements Serializable {

    private static final long serialVersionUID = 1L;

    private XmlProcess process;
    private Collection<XmlError> error;
    private String targetNamespace;

    public XmlProcess getProcess() {
        return process;
    }

    public void setProcess(XmlProcess process) {
        this.process = process;
    }

    public Collection<XmlError> getError() {
        return error;
    }

    public void setError(Collection<XmlError> error) {
        this.error = error;
    }

    @XmlAttribute
    public String getTargetNamespace() {
        return targetNamespace;
    }

    public void setTargetNamespace(String targetNamespace) {
        this.targetNamespace = targetNamespace;
    }
}
