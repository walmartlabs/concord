package com.walmartlabs.concord.plugins.bpmn.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "intermediateCatchEvent")
public class XmlIntermediateCatchEvent extends AbstractXmlElement {

    private static final long serialVersionUID = 1L;

    private XmlTimerEventDefinition timerDefinition;

    @XmlElement(namespace = Constants.MODEL_NS, name = "timerEventDefinition")
    public XmlTimerEventDefinition getTimerDefinition() {
        return timerDefinition;
    }

    public void setTimerDefinition(XmlTimerEventDefinition timerDefinition) {
        this.timerDefinition = timerDefinition;
    }
}
