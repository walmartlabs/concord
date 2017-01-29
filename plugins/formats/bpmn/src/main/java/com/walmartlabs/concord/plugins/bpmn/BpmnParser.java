package com.walmartlabs.concord.plugins.bpmn;

import com.walmartlabs.concord.plugins.bpmn.model.*;
import io.takari.bpm.model.*;
import io.takari.bpm.xml.Parser;
import io.takari.bpm.xml.ParserException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class BpmnParser implements Parser {

    public static final String TYPE = "concord/bpmn";
    private static final String TARGET_NAMESPACE = "http://bpmn.io/schema/bpmn";

    private final JAXBContext ctx;

    public BpmnParser() {
        try {
            ctx = JAXBContext.newInstance("com.walmartlabs.concord.plugins.bpmn.model");
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ProcessDefinition parse(InputStream in) throws ParserException {
        try {
            Unmarshaller m = ctx.createUnmarshaller();
            Object src = m.unmarshal(in);

            XmlDefinitions defs = (XmlDefinitions) src;
            if (!TARGET_NAMESPACE.equals(defs.getTargetNamespace())) {
                throw new ParserException("Unknown XML format: invalid 'targetNamespace' value");
            }

            return convert(defs);
        } catch (JAXBException e) {
            throw new ParserException("JAXB error", e);
        }
    }

    private static ProcessDefinition convert(XmlDefinitions defs) {
        XmlProcess p = defs.getProcess();
        return new ProcessDefinition(p.getId(), convert(p.getElements(), defs.getError()),
                Collections.singletonMap(ProcessDefinition.SOURCE_TYPE_ATTRIBUTE, TYPE));
    }

    private static Collection<AbstractElement> convert(Collection<AbstractXmlElement> elements, Collection<XmlError> errors) {
        Collection<AbstractElement> children = new ArrayList<>();

        for (AbstractXmlElement e : elements) {
            if (e instanceof XmlStartEvent) {
                convertStartEvent(children, (XmlStartEvent) e);
            } else if (e instanceof XmlEndEvent) {
                convertEndEvent(errors, children, (XmlEndEvent) e);
            } else if (e instanceof XmlSequenceFlow) {
                convertSequenceFlow(children, (XmlSequenceFlow) e);
            } else if (e instanceof XmlServiceTask) {
                convertServiceTask(children, (XmlServiceTask) e);
            } else if (e instanceof XmlUserTask) {
                convertUserTask(children, (XmlUserTask) e);
            } else if (e instanceof XmlEventBasedGateway) {
                convertEventBasedGateway(children, (XmlEventBasedGateway) e);
            } else if (e instanceof XmlExclusiveGateway) {
                convertExclusiveGateway(children, (XmlExclusiveGateway) e);
            } else if (e instanceof XmlInclusiveGateway) {
                convertInclusiveGateway(children, (XmlInclusiveGateway) e);
            } else if (e instanceof XmlIntermediateCatchEvent) {
                convertIntermediateCatchEvent(children, (XmlIntermediateCatchEvent) e);
            } else if (e instanceof XmlIntermediateThrowEvent) {
                convertIntermediateThrowEvent(children, (XmlIntermediateThrowEvent) e);
            } else if (e instanceof XmlCallActivity) {
                convertCallActivity(children, (XmlCallActivity) e);
            } else if (e instanceof XmlSubProcess) {
                convertSubProcess(errors, children, (XmlSubProcess) e);
            } else if (e instanceof XmlParallelGateway) {
                convertParallelGateway(children, (XmlParallelGateway) e);
            } else if (e instanceof XmlBoundaryEvent) {
                convertBoundaryEvent(errors, children, (XmlBoundaryEvent) e);
            }
        }

        return children;
    }

    private static void convertBoundaryEvent(Collection<XmlError> errors, Collection<AbstractElement> children, XmlBoundaryEvent e) {
        // TODO timer definitions
        String errorCode = null;
        if (e.getErrorEventDefinition() != null) {
            errorCode = findErrorCode(errors, e.getErrorEventDefinition().getErrorRef());
        }
        children.add(new BoundaryEvent(e.getId(), e.getAttachedToRef(), errorCode));
    }

    private static void convertParallelGateway(Collection<AbstractElement> children, XmlParallelGateway e) {
        children.add(new ParallelGateway(e.getId()));
    }

    private static void convertSubProcess(Collection<XmlError> errors, Collection<AbstractElement> children, XmlSubProcess e) {
        children.add(new SubProcess(e.getId(), convert(e.getElements(), errors)));
    }

    private static void convertCallActivity(Collection<AbstractElement> children, XmlCallActivity e) {
        // TODO variables mapping
        children.add(new CallActivity(e.getId(), e.getCalledElement()));
    }

    private static void convertIntermediateThrowEvent(Collection<AbstractElement> children, XmlIntermediateThrowEvent e) {
        // TODO attachments, error refs and timeouts
        children.add(new BoundaryEvent(e.getId(), null, null));
    }

    private static void convertIntermediateCatchEvent(Collection<AbstractElement> children, XmlIntermediateCatchEvent e) {
        // TODO message refs
        String timeDuration = null;
        if (e.getTimerDefinition() != null) {
            XmlTimerEventDefinition t = e.getTimerDefinition();
            timeDuration = t.getDuration();
        }
        children.add(new IntermediateCatchEvent(e.getId(), null, null, timeDuration));
    }

    private static void convertInclusiveGateway(Collection<AbstractElement> children, XmlInclusiveGateway e) {
        children.add(new InclusiveGateway(e.getId()));
    }

    private static void convertExclusiveGateway(Collection<AbstractElement> children, XmlExclusiveGateway e) {
        children.add(new ExclusiveGateway(e.getId(), e.getDefaultFlow()));
    }

    private static void convertEventBasedGateway(Collection<AbstractElement> children, XmlEventBasedGateway e) {
        children.add(new EventBasedGateway(e.getId()));
    }

    private static void convertUserTask(Collection<AbstractElement> children, XmlUserTask e) {
        // TODO attributes
        children.add(new ServiceTask(e.getId()));
    }

    private static void convertServiceTask(Collection<AbstractElement> children, XmlServiceTask e) {
        ExpressionType type = ExpressionType.NONE;
        String exprType = e.getExpressionType();
        if (exprType != null) {
            type = ExpressionType.valueOf(exprType.trim().toUpperCase());
        }
        children.add(new ServiceTask(e.getId(), type, e.getExpression()));
    }

    private static void convertSequenceFlow(Collection<AbstractElement> children, XmlSequenceFlow e) {
        children.add(new SequenceFlow(e.getId(), e.getSourceRef(), e.getTargetRef(), e.getExpression()));
    }

    private static void convertEndEvent(Collection<XmlError> errors, Collection<AbstractElement> children, XmlEndEvent e) {
        if (e.getErrorEventDefinition() != null) {
            String errorCode = findErrorCode(errors, e.getErrorEventDefinition().getErrorRef());
            children.add(new EndEvent(e.getId(), errorCode));
        } else {
            children.add(new EndEvent(e.getId()));
        }
    }

    private static void convertStartEvent(Collection<AbstractElement> children, XmlStartEvent e) {
        children.add(new StartEvent(e.getId()));
    }

    private static String findErrorCode(Collection<XmlError> errors, String ref) {
        if (errors == null || ref == null) {
            return null;
        }

        for (XmlError e : errors) {
            if (ref.equals(e.getId())) {
                return e.getErrorCode();
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return "Concord BPMN XML Parser";
    }
}
