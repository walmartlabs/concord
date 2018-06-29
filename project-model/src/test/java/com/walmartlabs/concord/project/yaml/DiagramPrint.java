package com.walmartlabs.concord.project.yaml;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import io.takari.bpm.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class DiagramPrint {

    public static void process(ProcessDefinition pd) {
        Collection<AbstractElement> elements = getElements(pd.getChildren());
        elements.forEach(e -> System.out.println(getName(e)));

        Map<String, List<AbstractElement>> a = new HashMap<>();
        List<AbstractElement> roots = new ArrayList<>();

        for(AbstractElement e : elements) {
            if(e instanceof SequenceFlow) {
                continue;
            }

            List<AbstractElement> parents = findParents(elements, e);
            if(parents.isEmpty()) {
                roots.add(e);
            } else {
                for(AbstractElement p : parents) {
                    if (!a.containsKey(p.getId())) {
                        a.put(p.getId(), new ArrayList<>());
                    }
                    a.get(p.getId()).add(e);
                }
            }
        }

        System.out.println("---------------------");
        print(a, roots);
        System.out.println("---------------------");
    }

    private static Collection<AbstractElement> getElements(Collection<AbstractElement> elements) {
        List<AbstractElement> result = new ArrayList<>();
        for(AbstractElement e : elements) {
            result.add(e);
            if(e instanceof ProcessDefinition) {
                ProcessDefinition s = (ProcessDefinition) e;
                result.addAll(getElements(s.getChildren()));
            }
        }

        return result;
    }

    private static void print(Map<String, List<AbstractElement>> nodes, List<AbstractElement> roots) {
        for(AbstractElement r : roots) {
            System.out.println(getName(r));

            List<AbstractElement> children = nodes.getOrDefault(r.getId(), new ArrayList<>());
            for(AbstractElement c : children) {
                print(c, nodes, "", true);
            }
            System.out.println("===");
        }
    }

    private static void print(AbstractElement node, Map<String, List<AbstractElement>> nodes, String prefix, boolean isTail) {
        System.out.println(prefix + (isTail ? "└── " : "├── ") + getName(node));

        List<AbstractElement> children = nodes.getOrDefault(node.getId(), new ArrayList<>());
        for (int i = 0; i < children.size() - 1; i++) {
            AbstractElement c = children.get(i);
            print(c, nodes, prefix + (isTail ? "    " : "│   "), false);
        }
        if (children.size() > 0) {
            AbstractElement c = children.get(children.size() - 1);
            print(c, nodes, prefix + (isTail ?"    " : "│   "), true);
        }
    }

    private static String getName(AbstractElement node) {
        String result = node.getId() + ": ";
        if(node instanceof EndEvent) {
            result += "EndEvent (" + ((EndEvent) node).getErrorRef() + ")";
        } else if(node instanceof ServiceTask) {
            ServiceTask serviceTask = (ServiceTask) node;
            String expr = serviceTask.getExpression();

            result += "ServiceTask (" + expr + ")";
        } else if(node instanceof ExclusiveGateway) {
            ExclusiveGateway gw = (ExclusiveGateway) node;
            result += "GW (" + gw.getDefaultFlow() + ")";
        } else if(node instanceof SequenceFlow) {
            SequenceFlow f = (SequenceFlow) node;
            result += f.getFrom() + " -> " + f.getTo() + " (" + f.getExpression() + ")";
        } else {
            result += node.getClass().getName();
        }

        return result;
    }

    private static List<AbstractElement> findParents(Collection<AbstractElement> elements, AbstractElement element) {
        if(element instanceof BoundaryEvent) {
            String pid = ((BoundaryEvent) element).getAttachedToRef();
            return new ArrayList<>(Collections.singletonList(find(elements, pid)));
        }
        return elements.stream()
            .filter(e -> e instanceof SequenceFlow)
            .map(e -> (SequenceFlow)e)
            .filter(e -> e.getTo().equals(element.getId()))
            .map(SequenceFlow::getFrom)
            .map(e -> find(elements, e))
            .collect(Collectors.toList());
    }

    private static AbstractElement find(Collection<AbstractElement> elements, String id) {
        return elements.stream()
                .filter(e -> e.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
