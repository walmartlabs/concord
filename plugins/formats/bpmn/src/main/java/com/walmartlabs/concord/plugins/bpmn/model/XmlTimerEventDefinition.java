package com.walmartlabs.concord.plugins.bpmn.model;

import javax.xml.bind.annotation.XmlElement;

public class XmlTimerEventDefinition extends AbstractXmlElement {

    private String duration;

    @XmlElement(name = "timeDuration")
    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }
}
