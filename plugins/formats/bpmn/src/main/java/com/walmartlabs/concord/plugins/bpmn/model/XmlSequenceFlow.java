package com.walmartlabs.concord.plugins.bpmn.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "sequenceFlow")
public class XmlSequenceFlow extends AbstractXmlElement {

    private static final long serialVersionUID = 1L;

    private String sourceRef;
    private String targetRef;
    private String expression;

    @XmlAttribute
    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    @XmlAttribute
    public String getTargetRef() {
        return targetRef;
    }

    public void setTargetRef(String targetRef) {
        this.targetRef = targetRef;
    }

    @XmlAttribute
    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }
}
