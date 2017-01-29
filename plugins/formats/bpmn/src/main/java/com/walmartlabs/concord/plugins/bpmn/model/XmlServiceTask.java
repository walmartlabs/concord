package com.walmartlabs.concord.plugins.bpmn.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "serviceTask")
public class XmlServiceTask extends AbstractXmlElement {

    private static final long serialVersionUID = 1L;

    private String expressionType;
    private String expression;

    @XmlAttribute(namespace = Constants.CONCORD_NS)
    public String getExpressionType() {
        return expressionType;
    }

    public void setExpressionType(String expressionType) {
        this.expressionType = expressionType;
    }

    @XmlAttribute(namespace = Constants.CONCORD_NS)
    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }
}
