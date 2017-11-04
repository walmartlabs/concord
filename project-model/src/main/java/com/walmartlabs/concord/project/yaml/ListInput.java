package com.walmartlabs.concord.project.yaml;

import io.takari.parc.Input;

import java.util.List;

public class ListInput<T> implements Input<T> {

    private final int pos;
    private final List<T> items;

    public ListInput(List<T> items) {
        this(0, items);
    }

    private ListInput(int pos, List<T> items) {
        this.pos = pos;
        this.items = items;
    }

    @Override
    public int position() {
        return pos;
    }

    @Override
    public T first() {
        return items.get(pos);
    }

    @Override
    public Input<T> rest() {
        return new ListInput<>(pos + 1, items);
    }

    @Override
    public boolean end() {
        return pos >= items.size();
    }
}
