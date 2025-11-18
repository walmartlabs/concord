/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 *
 * @author Kin-man Chung
 */

package com.sun.el.stream;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.sun.el.lang.ELSupport;
import com.sun.el.lang.ELArithmetic;

import java.lang.reflect.Array;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import javax.el.ELException;
import javax.el.LambdaExpression;


/*
 */

public class Stream {

    private Iterator<Object> source;
    private Stream upstream;
    private Operator op;

    Stream(Iterator<Object> source) {
        this.source = source;
    }

    Stream(Stream upstream, Operator op) {
        this.upstream = upstream;
        this.op = op;
    }

    public Iterator<Object> iterator() {
        if (source != null) {
            return source;
        }

        return op.iterator(upstream.iterator());
    }

    public Stream filter(final LambdaExpression predicate) {
        return new Stream(this, new Operator() {
            @Override
            public Iterator<Object> iterator(final Iterator<Object> upstream) {
                return new Iterator2(upstream) {
                    @Override
                    public void doItem(Object item) {
                        if ((Boolean) predicate.invoke(item)) {
                            _yield(item);
                        }
                    }
                };
            }
        });
    }

    public Stream map(final LambdaExpression mapper) {
        return new Stream(this, new Operator() {
            @Override
            public Iterator<Object> iterator(final Iterator<Object> up) {
                return new Iterator1(up) {
                    @Override
                    public Object next() {
                        return mapper.invoke(iter.next());
                    }
                };
            }
        });
    }

    public Stream peek(final LambdaExpression comsumer) {
        return new Stream(this, new Operator() {
            @Override
            public Iterator<Object> iterator(final Iterator<Object> up) {
               return new Iterator2(up) {
                    @Override
                    void doItem(Object item){
                        comsumer.invoke(item);
                        _yield(item);
                    }
                };
            }
        });
    }

    public Stream limit(final long n) {
        if (n < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }
        return new Stream(this, new Operator() {
            @Override
            public Iterator<Object> iterator(final Iterator<Object> up) {
                return new Iterator0() {
                    long limit = n;
                    @Override
                    public boolean hasNext() {
                        return (limit > 0)? up.hasNext(): false;
                    }
                    @Override
                    public Object next() {
                        limit--;
                        return up.next();
                    }
                };
            }
        });
    }

    public Stream substream(final long startIndex) {
        if (startIndex < 0) {
            throw new IllegalArgumentException("substream index must be non-negative");
        }
        return new Stream(this, new Operator() {
            long skip = startIndex;
            @Override
            public Iterator<Object> iterator(final Iterator<Object> up) {
                while (skip > 0 && up.hasNext()) {
                    up.next();
                    skip--;
                }
                return up;
            }
        });
    }

    public Stream substream(long startIndex, long endIndex) {
        return substream(startIndex).limit(endIndex-startIndex);
    }

    public Stream distinct () {
        return new Stream(this, new Operator() {
            @Override
            public Iterator<Object> iterator(final Iterator<Object> up) {
                return new Iterator2(up) {
                    private Set<Object> set = new HashSet<Object>();
                    @Override
                    public void doItem(Object item) {
                        if (set.add(item)) {
                            _yield(item);
                        }
                    }
                };
            }
        });
    }

    public Stream sorted() {
        return new Stream(this, new Operator() {

            private PriorityQueue<Object> queue = null;

            @Override
            public Iterator<Object> iterator(final Iterator<Object> up) {
                if (queue == null) {
                    queue = new PriorityQueue<Object>(16,
                        new Comparator<Object>() {
                            @Override
                            public int compare(Object o1, Object o2) {
                                return ((Comparable)o1).compareTo(o2);
                            }
                        });

                    while(up.hasNext()) {
                        queue.add(up.next());
                    }
                }

                return new Iterator0() {
                    @Override
                    public boolean hasNext() {
                        return !queue.isEmpty();
                    }
                    @Override
                    public Object next() {
                         return queue.remove();
                    }
                };
            }
        });
    }

    public Stream sorted(final LambdaExpression comparator) {
        return new Stream(this, new Operator() {

            private PriorityQueue<Object> queue = null;

            @Override
            public Iterator<Object> iterator(final Iterator<Object> up) {
                if (queue == null) {
                    queue = new PriorityQueue<Object>(16,
                        new Comparator<Object>() {
                            @Override
                            public int compare(Object o1, Object o2) {
                                return (Integer) ELSupport.coerceToType(
                                    comparator.invoke(o1, o2),
                                    Integer.class);
                            }
                        });

                    while(up.hasNext()) {
                        queue.add(up.next());
                    }
                }

                return new Iterator0() {
                    @Override
                    public boolean hasNext() {
                        return !queue.isEmpty();
                    }
                    @Override
                    public Object next() {
                         return queue.remove();
                    }
                };
            }
        });
    }

    public Stream flatMap(final LambdaExpression mapper) {
        return new Stream(this, new Operator() {
            @Override
            public Iterator<Object> iterator(final Iterator<Object> upstream) {
                return new Iterator0() {
                    Iterator<Object> iter = null;
                    @Override
                    public boolean hasNext() {
                        while (true) {
                            if (iter == null) {
                                if (!upstream.hasNext()) {
                                    return false;
                                }
                                Object mapped = mapper.invoke(upstream.next());
                                if (! (mapped instanceof Stream)) {
                                   throw new ELException("Expecting a Stream " +
                                           "from flatMap's mapper function.");
                                }
                                iter = ((Stream)mapped).iterator();
                            }
                            else {
                                if (iter.hasNext()) {
                                    return true;
                                }
                                iter = null;
                            }
                        }
                    }
                    @Override
                    public Object next() {
                        if (iter == null) {
                            return null;
                        }
                        return iter.next();
                    }
                };
            }
        });
    }

    public Object reduce(Object base, LambdaExpression op) {
        Iterator<Object> iter = iterator();
        while (iter.hasNext()) {
            base = op.invoke(base, iter.next());
        }
        return base;
    }

    public Optional reduce(LambdaExpression op) {
        Iterator<Object> iter = iterator();
        if (iter.hasNext()) {
            Object base = iter.next();
            while (iter.hasNext()) {
                base = op.invoke(base, iter.next());
            }
            return new Optional(base);
        }
        return new Optional();
    }

/*
    public Map<Object,Object> reduceBy(LambdaExpression classifier,
                                       LambdaExpression seed,
                                       LambdaExpression reducer) {
        Map<Object,Object> map = new HashMap<Object,Object>();
        Iterator<Object> iter = iterator();
        while (iter.hasNext()) {
            Object item = iter.next();
            Object key = classifier.invoke(item);
            Object value = map.get(key);
            if (value == null) {
                value = seed.invoke();
            }
            map.put(key, reducer.invoke(value, item));
        }
        return map;
    }
*/

    public void forEach(LambdaExpression comsumer) {
        Iterator<Object> iter = iterator();
        while (iter.hasNext()) {
            comsumer.invoke(iter.next());
        }
    }

/*
    public Map<Object,Collection<Object>> groupBy(LambdaExpression classifier) {
        Map<Object, Collection<Object>> map =
                        new HashMap<Object, Collection<Object>>();
        Iterator<Object> iter = iterator();
        while (iter.hasNext()) {
            Object item = iter.next();
            Object key = classifier.invoke(item);
            if (key == null) {
                throw new ELException("null key");
            }
            Collection<Object> c = map.get(key);
            if (c == null) {
                c = new ArrayList<Object>();
                map.put(key, c);
            }
            c.add(item);
        }
        return map;
    }
*/
    public boolean anyMatch(LambdaExpression predicate) {
        Iterator<Object> iter = iterator();
        while (iter.hasNext()) {
            if ((Boolean) predicate.invoke(iter.next())) {
                return true;
            }
        }
        return false;
    }

    public boolean allMatch(LambdaExpression predicate) {
        Iterator<Object> iter = iterator();
        while (iter.hasNext()) {
            if (! (Boolean) predicate.invoke(iter.next())) {
                return false;
            }
        }
        return true;
    }

    public boolean noneMatch(LambdaExpression predicate) {
        Iterator<Object> iter = iterator();
        while (iter.hasNext()) {
            if ((Boolean) predicate.invoke(iter.next())) {
                return false;
            }
        }
        return true;
    }

    public Object[] toArray() {
        Iterator<Object> iter = iterator();
        ArrayList<Object> al = new ArrayList<Object>();
        while (iter.hasNext()) {
            al.add(iter.next());
        }
        return al.toArray();
    }

    public Object toList() {
        Iterator<Object> iter = iterator();
        ArrayList<Object> al = new ArrayList<Object>();
        while (iter.hasNext()) {
            al.add(iter.next());
        }
        return al;
    }

/*
    public Object into(Object target) {
        if (! (target instanceof Collection)) {
            throw new ELException("The argument type for into operation mush be a Collection");
        }
        Collection<Object> c = (Collection<Object>) target;
        Iterator<Object> iter = iterator();
        while (iter.hasNext()) {
            c.add(iter.next());
        }
        return c;
    }
*/

    public Optional findFirst() {
        Iterator<Object> iter = iterator();
        if (iter.hasNext()) {
            return new Optional(iter.next());
        } else {
            return new Optional();
        }
    }

    public Object sum() {
        Number sum = Long.valueOf(0);
        Iterator<Object> iter = iterator();
        while (iter.hasNext()) {
            sum = ELArithmetic.add(sum, iter.next());
        }
        return sum;
    }

    public Object count() {
        long count = 0;
        Iterator<Object> iter = iterator();
        while (iter.hasNext()) {
            count++;
            iter.next();
        }
        return Long.valueOf(count);
    }

    public Optional min() {
        Object min = null;
        Iterator<Object> iter = iterator();
        while (iter.hasNext()) {
            Object item = iter.next();
            if (min == null || ELSupport.compare(min, item) > 0) {
                min = item;
            }
        }
        if (min == null) {
            return new Optional();
        }
        return new Optional(min);
    }

    public Optional max() {
        Object max = null;
        Iterator<Object> iter = iterator();
        while (iter.hasNext()) {
            Object item = iter.next();
            if (max == null || ELSupport.compare(max, item) < 0) {
                max = item;
            }
        }
        if (max == null) {
            return new Optional();
        }
        return new Optional(max);
    }

    public Optional min(final LambdaExpression comparator) {
        Object min = null;
        Iterator<Object> iter = iterator();
        while (iter.hasNext()) {
            Object item = iter.next();
            if (min == null ||
                    ELSupport.compare(comparator.invoke(item, min), Long.valueOf(0)) < 0) {
                min = item;
            }
        }
        if (min == null) {
            return new Optional();
        }
        return new Optional(min);
    }

    public Optional max(final LambdaExpression comparator) {
        Object max = null;
        Iterator<Object> iter = iterator();
        while (iter.hasNext()) {
            Object item = iter.next();
            if (max == null ||
                    ELSupport.compare(comparator.invoke(max, item), Long.valueOf(0)) < 0) {
                max = item;
            }
        }
        if (max == null) {
            return new Optional();
        }
        return new Optional(max);
    }

    public Optional average() {
        Number sum = Long.valueOf(0);
        long count = 0;
        Iterator<Object> iter = iterator();
        while (iter.hasNext()) {
            count++;
            sum = ELArithmetic.add(sum, iter.next());
        }
        if (count == 0) {
            return new Optional();
        }
        return new Optional(ELArithmetic.divide(sum, count));
    }

    abstract class Iterator0 implements Iterator<Object> {
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    abstract class Iterator1 extends Iterator0 {

        Iterator iter;
        Iterator1(Iterator iter) {
            this.iter = iter;
        }

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }
    }

    abstract class Iterator2 extends Iterator1 {
        private Object current;
        private boolean yielded;

        Iterator2(Iterator upstream) {
            super(upstream);
        }

       @Override
        public Object next() {
            yielded = false;
            return current;
        }

        @Override
        public boolean hasNext() {
            while ((!yielded) && iter.hasNext()) {
                doItem(iter.next());
            }
            return yielded;
        }

        void _yield(Object current) {
            this.current = current;
            yielded = true;
        }

        abstract void doItem(Object item);
    }
}
