package com.walmartlabs.concord.plugins.bpmn.model;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;

@XmlType
@XmlSeeAlso({
        XmlBoundaryEvent.class,
        XmlCallActivity.class,
        XmlEndEvent.class,
        XmlEventBasedGateway.class,
        XmlExclusiveGateway.class,
        XmlInclusiveGateway.class,
        XmlIntermediateCatchEvent.class,
        XmlIntermediateThrowEvent.class,
        XmlSequenceFlow.class,
        XmlServiceTask.class,
        XmlStartEvent.class,
        XmlSubProcess.class,
        XmlTimerEventDefinition.class,
        XmlUserTask.class
})
public class AbstractXmlElement implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    @XmlAttribute
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
