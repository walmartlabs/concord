package com.walmartlabs.concord.plugins.yaml;

import com.walmartlabs.concord.common.format.ParserException;
import io.takari.bpm.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public final class YamlConverter {

    private static final Logger log = LoggerFactory.getLogger(YamlParser.class);

    public static final String TYPE = "concord/yaml";

    private static final String SUBPROCESS_KEY = "subprocess";
    private static final String SWITCH_KEY = "switch";
    private static final String STEPS_KEY = "steps";
    private static final String EXPR_KEY = "expr";
    private static final String END_KEY = "end";
    private static final String CALL_KEY = "call";
    private static final String REF_KEY = "ref";
    private static final String IN_VARS_KEY = "in";
    private static final String ERRORS_KEY = "errors";

    public static Collection<ProcessDefinition> convert(Map<String, Object> data) throws ParserException {
        Collection<ProcessDefinition> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String id = entry.getKey();

            Object v = entry.getValue();
            if (!(v instanceof List)) {
                throw new ParserException("Unsupported element type in the process '" + id + "': " + v);
            }

            List<Object> l = (List<Object>) v;
            IdGenerator idGenerator = new IdGenerator();

            ProcessDefinition pd = new ProcessDefinition(id, addEntryPoint(idGenerator, toElements(idGenerator, l)),
                    Collections.singletonMap(ProcessDefinition.SOURCE_TYPE_ATTRIBUTE, TYPE));

            if (log.isDebugEnabled()) {
                print(pd, 0);
            }

            result.add(pd);
        }

        return result;
    }

    private static void print(AbstractElement e, int level) {
        if (e instanceof SequenceFlow) {
            SequenceFlow f = (SequenceFlow) e;
            System.out.println(ident(level) + e.getClass() + ": id=" + e.getId() + " // " + f.getFrom() + " -> " + f.getTo() + " // " + f.getExpression());
        } else if (e instanceof ServiceTask) {
            ServiceTask t = (ServiceTask) e;
            System.out.println(ident(level) + e.getClass() + ": id=" + e.getId() + " // " + t.getType() + " // " + t.getExpression());
        } else {
            System.out.println(ident(level) + e.getClass() + ": id=" + e.getId());
        }

        if (e instanceof ProcessDefinition) {
            ProcessDefinition pd = (ProcessDefinition) e;
            for (AbstractElement c : pd.getChildren()) {
                print(c, level + 1);
            }
        }
    }

    private static String ident(int level) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < level; i++) {
            b.append("\t");
        }
        return b.toString();
    }

    private static List<AbstractElement> toElements(IdGenerator idGenerator, Collection<Object> elements) throws ParserException {
        List<Container> result = new ArrayList<>();

        for (Object e : elements) {
            if (!(e instanceof Map)) {
                throw new ParserException("Unsupported element type: " + e);
            }

            Map<String, Object> m = (Map<String, Object>) e;
            result.add(toElements(idGenerator, m));
        }

        return link(idGenerator, result);
    }

    private static Container toElements(IdGenerator idGenerator, Map<String, Object> m) throws ParserException {
        if (m.containsKey(SUBPROCESS_KEY)) {
            return toSubProcess(idGenerator, m);
        }

        if (m.containsKey(EXPR_KEY)) {
            ServiceTask t = new ServiceTask(idGenerator.nextId(), ExpressionType.SIMPLE, m.get("expr").toString());
            return new Container(t, t);
        }

        if (m.containsKey(END_KEY)) {
            String errorRef = (String) m.get(END_KEY);
            EndEvent e = new EndEvent(idGenerator.nextId(), errorRef);
            return new Container(e, e);
        }

        if (m.containsKey(CALL_KEY)) {
            return toCallActivity(idGenerator, m);
        }

        if (m.containsKey(SWITCH_KEY)) {
            return toSwitch(idGenerator, m);
        }

        throw new ParserException("Unsupported element type: " + m);
    }

    private static Container toSwitch(IdGenerator idGenerator, Map<String, Object> m) throws ParserException {
        Object o = m.get(SWITCH_KEY);
        if (o == null) {
            throw new ParserException("Empty switch branches, should be at least one");
        }

        if (!(o instanceof Collection)) {
            throw new ParserException("Invalid switch branches type. Should be an array of objects");
        }

        ExclusiveGateway gw = new ExclusiveGateway(idGenerator.nextId());
        List<AbstractElement> elements = new ArrayList<>();

        List<AbstractElement> exits = new ArrayList<>();
        exits.add(gw); // a default branch

        Collection<Map<String, Object>> branches = (Collection<Map<String, Object>>) o;
        for (Map<String, Object> b : branches) {
            Object steps = b.get(STEPS_KEY);
            if (steps == null || ((Collection<Object>) steps).isEmpty()) {
                throw new ParserException("A branch must contain at least one step");
            }

            List<AbstractElement> es = toElements(idGenerator, (Collection<Object>) steps);

            Object expr = b.get(EXPR_KEY);
            if (expr != null && !(expr instanceof String)) {
                throw new ParserException("Branch expression must be a string");
            }

            String toId = es.get(0).getId();

            // if the branch doesn't finish with an end event, then add its last element as an 'exit' element
            if (es.size() >= 2) {
                AbstractElement exit = es.get(es.size() - 1);
                if (!(exit instanceof EndEvent)) {
                    exits.add(exit);
                }
            }

            SequenceFlow f = new SequenceFlow(idGenerator.nextId(), gw.getId(), toId, (String) expr);
            elements.add(f);
            elements.addAll(es);
        }

        return new Container(gw, exits, elements);
    }

    private static Container toCallActivity(IdGenerator idGenerator, Map<String, Object> m) throws ParserException {
        String call = (String) m.get(CALL_KEY);
        CallActivity a = new CallActivity(idGenerator.nextId(), call, toVariableMapping(m), null);
        return new Container(a, a);
    }

    private static Set<VariableMapping> toVariableMapping(Map<String, Object> m) throws ParserException {
        Object o = m.get(IN_VARS_KEY);
        if (o == null) {
            return null;
        }

        if (!(o instanceof Map)) {
            throw new ParserException("Invalid element type of input variables mapping: " + o);
        }

        Set<VariableMapping> result = new HashSet<>();
        Map<String, Object> vars = (Map<String, Object>) o;

        for (Map.Entry<String, Object> e : vars.entrySet()) {
            String sourceExpression = null;
            Object sourceValue = null;
            String target = e.getKey();

            Object v = e.getValue();

            if (v instanceof Number || v instanceof Boolean || v instanceof Collection) {
                sourceValue = v;
            } else if (v instanceof String) {
                String s = v.toString();
                if (isAnExpression(s)) {
                    sourceExpression = s;
                } else {
                    sourceValue = s;
                }
            } else {
                throw new ParserException("Source variable values must be a string, an expression or a supported literal value. Key: " + target + ", value: " + v);
            }

            result.add(new VariableMapping(null, sourceExpression, sourceValue, target));
        }

        return result;
    }

    private static Container toSubProcess(IdGenerator idGenerator, Map<String, Object> m) throws ParserException {
        Object steps = m.get(STEPS_KEY);
        if (steps == null) {
            throw new ParserException("Mandatory 'steps' section missing in the subprocess: " + m);
        }

        if (!(steps instanceof List)) {
            throw new ParserException("Unsupported element in the steps section of the subprocess: " + m);
        }

        List<Object> l = (List<Object>) steps;
        if (l.isEmpty()) {
            throw new ParserException("Empty 'steps' section in the subprocess: " + m);
        }

        List<AbstractElement> children = addEntryPoint(idGenerator, toElements(idGenerator, l));

        String subId = idGenerator.nextId();
        SubProcess sub = new SubProcess(subId, children);

        return new Container(sub, sub, toBoundaryEvents(idGenerator, subId, m));
    }

    private static List<AbstractElement> toBoundaryEvents(IdGenerator idGenerator, String attachedTo, Map<String, Object> m) throws ParserException {
        Object errors = m.get(ERRORS_KEY);
        if (errors == null) {
            return Collections.emptyList();
        }

        if (!(errors instanceof List)) {
            throw new ParserException("Invalid errors description: expected a list of errors: " + m);
        }

        List<Object> el = (List<Object>) errors;
        if (el.isEmpty()) {
            throw new ParserException("Emptry 'errors' section in the subprocess: " + m);
        }

        List<AbstractElement> result = new ArrayList<>();
        for (Object e : el) {
            if (!(e instanceof Map)) {
                throw new ParserException("Invalid format of error description. Expected a map, got: " + e);
            }

            Map<String, Object> em = (Map<String, Object>) e;
            String ref = (String) em.get(REF_KEY);
            String call = (String) em.get(CALL_KEY);
            if (call == null) {
                throw new ParserException("Invalid format of error description. Expected a 'call' element, got: " + e);
            }

            String evId = idGenerator.nextId();
            result.add(new BoundaryEvent(evId, attachedTo, ref));

            Container a = toCallActivity(idGenerator, em);

            String callId = a.first.getId();
            result.add(new SequenceFlow(idGenerator.nextId(), evId, callId));
            result.add(a.first);

            if (a.rest != null) {
                result.addAll(a.rest);
            }

            String endId = idGenerator.nextId();
            if (a.last != null) {
                for (AbstractElement l : a.last) {
                    result.add(new SequenceFlow(idGenerator.nextId(), l.getId(), endId));
                }
                result.addAll(a.last);
            }

            result.add(new EndEvent(endId, ref != null ? ref : "generalError"));
        }

        return result;
    }

    private static List<AbstractElement> addEntryPoint(IdGenerator idGenerator, List<AbstractElement> elements) throws ParserException {
        if (elements.size() < 1) {
            throw new ParserException("Can't add an entry point to a empty block");
        }

        List<AbstractElement> l = new ArrayList<>(elements);

        AbstractElement first = l.get(0);
        AbstractElement last = l.get(l.size() - 1);

        String startId = idGenerator.nextId();
        String endId = idGenerator.nextId();

        l.add(0, new StartEvent(startId));
        l.add(1, new SequenceFlow(idGenerator.nextId(), startId, first.getId()));
        l.add(new SequenceFlow(idGenerator.nextId(), last.getId(), endId));
        l.add(new EndEvent(endId));

        return l;
    }

    private static List<AbstractElement> link(IdGenerator idGenerator, List<Container> containers) {
        if (containers.isEmpty()) {
            return Collections.emptyList();
        }

        List<AbstractElement> l = new ArrayList<>();

        Collection<AbstractElement> last = null;
        for (Container c : containers) {
            if (last != null) {
                for (AbstractElement e : last) {
                    l.add(new SequenceFlow(idGenerator.nextId(), e.getId(), c.first.getId()));
                }
            }

            l.add(c.first);
            if (c.rest != null) {
                l.addAll(c.rest);
            }
            l.addAll(c.last);

            last = c.last;
        }

        return l;
    }

    private static boolean isAnExpression(String s) {
        if (s == null) {
            return false;
        }

        return s.startsWith("${") && s.endsWith("}");
    }

    private static class IdGenerator {

        private int counter = 0;

        public String nextId() {
            return "e" + counter++;
        }
    }

    private static class Container {

        private final AbstractElement first;
        private final List<AbstractElement> last;
        private final List<AbstractElement> rest;

        private Container(AbstractElement first, List<AbstractElement> last, List<AbstractElement> rest) {
            this.first = first;
            this.last = last;
            this.rest = rest;
        }

        private Container(AbstractElement first, AbstractElement last, List<AbstractElement> rest) {
            this(first, Collections.singletonList(last), rest);
        }

        private Container(AbstractElement first, AbstractElement last) {
            this(first, last, null);
        }
    }

    private YamlConverter() {
    }
}
