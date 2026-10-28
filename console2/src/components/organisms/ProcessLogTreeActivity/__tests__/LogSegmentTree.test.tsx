/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import * as React from 'react';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';

import { ProcessStatus } from '../../../../api/process';
import { SegmentKind, SegmentStatus } from '../../../../api/process/log';
import type { LogSegmentEntry } from '../../../../api/process/log';
import LogSegmentTree from '../LogSegmentTree';
import { buildSegmentTree } from '../segmentTree';
import type { SegmentTree } from '../segmentTree';

const segment = (id: number, extra: Partial<LogSegmentEntry> = {}): LogSegmentEntry => ({
    id,
    name: `step-${id}`,
    createdAt: new Date(1_000 + id).toISOString(),
    ...extra,
});

let viewportHeight = 280;
let originalHeight: PropertyDescriptor | undefined;
const observers = new Set<GeometryObserver>();
class GeometryObserver {
    elements = new Set<Element>();
    constructor(private callback: ResizeObserverCallback) {
        observers.add(this);
    }
    observe(element: Element) {
        this.elements.add(element);
    }
    disconnect() {
        this.elements.clear();
        observers.delete(this);
    }
    deliver() {
        this.callback([], this as unknown as ResizeObserver);
    }
}

beforeEach(() => {
    viewportHeight = 280;
    originalHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientHeight');
    Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
        configurable: true,
        get() {
            return this.classList.contains('TreeRows') ? viewportHeight : 0;
        },
    });
    vi.stubGlobal('ResizeObserver', GeometryObserver);
});

afterEach(() => {
    cleanup();
    if (originalHeight) {
        Object.defineProperty(HTMLElement.prototype, 'clientHeight', originalHeight);
    } else {
        Reflect.deleteProperty(HTMLElement.prototype, 'clientHeight');
    }
    observers.clear();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    vi.useRealTimers();
});

const flatSegments = () => Array.from({ length: 5000 }, (_, i) => segment(i + 1));
const mountedIds = () =>
    screen.getAllByRole('treeitem').map((row) => Number(row.getAttribute('data-segment-id')));
const rowOf = (id: number) => {
    const row = document.querySelector(`[data-segment-id="${id}"]`);
    expect(row).not.toBeNull();
    return row as HTMLElement;
};
const expectSelected = (id: number) => {
    expect(rowOf(id).getAttribute('aria-selected')).toBe('true');
    const rows = screen.getByRole('tree');
    const top =
        mountedIds().indexOf(id) * 28 +
        Number((rows.firstElementChild as HTMLElement).style.height.replace('px', ''));
    expect(top).toBeGreaterThanOrEqual(rows.scrollTop);
    expect(top + 28).toBeLessThanOrEqual(rows.scrollTop + rows.clientHeight);
};

const Harness = ({ tree, initialId }: { tree: SegmentTree; initialId?: number }) => {
    const [selectedId, setSelectedId] = React.useState(initialId);
    const onSelect = React.useCallback((id: number) => setSelectedId(id), []);
    return (
        <LogSegmentTree
            tree={tree}
            selectedId={selectedId}
            processStatus={ProcessStatus.FINISHED}
            onSelect={onSelect}
        />
    );
};

test('keeps a selected Running filter visible when its count reaches zero', () => {
    const onSelect = vi.fn();
    const view = render(
        <LogSegmentTree
            tree={buildSegmentTree([segment(1, { status: SegmentStatus.RUNNING })])}
            processStatus={ProcessStatus.RUNNING}
            onSelect={onSelect}
        />
    );
    fireEvent.click(screen.getByText('Running'));

    view.rerender(
        <LogSegmentTree
            tree={buildSegmentTree([segment(1, { status: SegmentStatus.OK })])}
            processStatus={ProcessStatus.FINISHED}
            onSelect={onSelect}
        />
    );

    const running = screen.getByText('Running').closest('.item');
    expect(running?.textContent).toBe('Running0');
    expect(running?.classList.contains('active')).toBe(true);
    expect(screen.getByText('No steps match the filter.')).toBeTruthy();
});

test('error navigation includes failures followed by a later retry', () => {
    const tree = buildSegmentTree([
        segment(1, { kind: SegmentKind.STEP, status: SegmentStatus.OK }),
        segment(2, {
            parentId: 1,
            kind: SegmentKind.ATTEMPT,
            attemptIndex: 0,
            status: SegmentStatus.FAILED,
            errors: 1,
        }),
        segment(3, {
            parentId: 2,
            status: SegmentStatus.FAILED,
            errors: 2,
        }),
        segment(4, {
            parentId: 1,
            kind: SegmentKind.ATTEMPT,
            attemptIndex: 1,
            status: SegmentStatus.OK,
        }),
    ]);

    render(
        <LogSegmentTree tree={tree} processStatus={ProcessStatus.FINISHED} onSelect={vi.fn()} />
    );

    expect(screen.queryByText('No errors')).toBeNull();
    expect(screen.getByLabelText('Previous error')).toHaveProperty('className');
    expect(screen.getByLabelText('Next error').classList.contains('disabled')).toBe(false);
    expect(tree.byId.get(3)?.retried).toBe(false);
});

test('bounds a 5000-row tree while scrolling and resizing the viewport', () => {
    render(<Harness tree={buildSegmentTree(flatSegments())} />);
    expect(mountedIds()).toEqual(Array.from({ length: 20 }, (_, i) => i + 1));
    const rows = screen.getByRole('tree');
    rows.scrollTop = 2500 * 28;
    fireEvent.scroll(rows);
    expect(mountedIds()).toEqual(Array.from({ length: 30 }, (_, i) => 2491 + i));
    expect(rowOf(2501).textContent).toContain('step-2501');
    expect(new Set(mountedIds()).size).toBe(mountedIds().length);
    expect(mountedIds().length).toBeLessThanOrEqual(3 * Math.ceil(viewportHeight / 28) + 1);
    act(() => {
        viewportHeight = 560;
        observers.forEach((observer) => observer.deliver());
    });
    expect(mountedIds()).toEqual(Array.from({ length: 60 }, (_, i) => 2481 + i));
    expect(mountedIds().length).toBeLessThanOrEqual(3 * Math.ceil(viewportHeight / 28) + 1);
});

test('defers offscreen selection until a zero-height viewport becomes measurable', () => {
    viewportHeight = 0;
    render(<Harness tree={buildSegmentTree(flatSegments())} initialId={4900} />);
    expect(mountedIds()).toEqual([1, 2, 3, 4, 5]);
    act(() => {
        viewportHeight = 280;
        observers.forEach((observer) => observer.deliver());
    });
    expectSelected(4900);
    expect(mountedIds().length).toBeLessThanOrEqual(31);
});

test('scrolls an unmounted selection into view but leaves visible selections in place', () => {
    const tree = buildSegmentTree(flatSegments());
    const onSelect = vi.fn();
    const view = render(<LogSegmentTree tree={tree} onSelect={onSelect} />);
    view.rerender(<LogSegmentTree tree={tree} onSelect={onSelect} selectedId={4900} />);
    expectSelected(4900);
    const rows = screen.getByRole('tree');
    const offset = rows.scrollTop;
    view.rerender(<LogSegmentTree tree={tree} onSelect={onSelect} selectedId={4899} />);
    expectSelected(4899);
    expect(rows.scrollTop).toBe(offset);
});

test('keyboard adjacency crosses window boundaries and clamps at logical endpoints', () => {
    const tree = buildSegmentTree(flatSegments());
    const view = render(<Harness tree={tree} initialId={1} />);
    const rows = screen.getByRole('tree');
    fireEvent.keyDown(rows, { key: 'ArrowUp' });
    expectSelected(1);
    for (let id = 2; id <= 25; id++) {
        fireEvent.keyDown(rows, { key: 'ArrowDown' });
        expectSelected(id);
    }
    for (let id = 24; id >= 1; id--) {
        fireEvent.keyDown(rows, { key: 'ArrowUp' });
        expectSelected(id);
    }
    view.unmount();
    render(<Harness tree={tree} initialId={5000} />);
    fireEvent.keyDown(screen.getByRole('tree'), { key: 'ArrowDown' });
    expectSelected(5000);
});

// loops start collapsed: two of them, the first iteration has 100 steps
const iteration = (id: number, parentId: number, index: number) =>
    segment(id, { parentId, kind: SegmentKind.ITERATION, iterationIndex: index });
const loopSegments = () => [
    segment(1, { kind: SegmentKind.LOOP }),
    iteration(2, 1, 0),
    ...Array.from({ length: 100 }, (_, i) => segment(i + 3, { parentId: 2 })),
    iteration(103, 1, 1),
    segment(104, { kind: SegmentKind.LOOP }),
    iteration(105, 104, 0),
    iteration(106, 104, 1),
];

test('Right and Left navigate collapsed loops; external child selection expands ancestors', () => {
    const tree = buildSegmentTree(loopSegments());
    const groupId = tree.roots[0].segment.id;
    const view = render(<Harness tree={tree} initialId={groupId} />);
    const rows = screen.getByRole('tree');
    expect(rowOf(groupId).getAttribute('aria-expanded')).toBe('false');
    fireEvent.keyDown(rows, { key: 'ArrowRight' });
    expect(rowOf(groupId).getAttribute('aria-expanded')).toBe('true');
    fireEvent.keyDown(rows, { key: 'ArrowRight' });
    expectSelected(2);
    fireEvent.keyDown(rows, { key: 'ArrowLeft' });
    expect(rowOf(2).getAttribute('aria-expanded')).toBe('false');
    fireEvent.keyDown(rows, { key: 'ArrowLeft' });
    expectSelected(groupId);
    fireEvent.keyDown(rows, { key: 'ArrowLeft' });
    expect(rowOf(groupId).getAttribute('aria-expanded')).toBe('false');
    view.unmount();
    render(<Harness tree={tree} initialId={100} />);
    expectSelected(100);
    expect(mountedIds().length).toBeLessThanOrEqual(31);
});

test('error navigation reaches unmounted and handled failures', () => {
    const entries = flatSegments();
    entries[0] = segment(1, { kind: SegmentKind.STEP, status: SegmentStatus.OK });
    entries[1] = segment(2, {
        parentId: 1,
        kind: SegmentKind.ATTEMPT,
        attemptIndex: 0,
        status: SegmentStatus.FAILED,
        errors: 1,
    });
    entries[2] = segment(3, {
        parentId: 1,
        kind: SegmentKind.ATTEMPT,
        attemptIndex: 1,
        status: SegmentStatus.OK,
    });
    entries[2499] = segment(2500, { status: SegmentStatus.FAILED });
    entries[4899] = segment(4900, { status: SegmentStatus.FAILED, errors: 2 });
    render(<Harness tree={buildSegmentTree(entries)} initialId={3} />);
    fireEvent.click(screen.getByLabelText('Next error'));
    expectSelected(2500);
    fireEvent.click(screen.getByLabelText('Next error'));
    expectSelected(4900);
    fireEvent.click(screen.getByLabelText('Next error'));
    expectSelected(2);
    fireEvent.click(screen.getByLabelText('Previous error'));
    expectSelected(4900);
    expect(screen.getByText('Error 3 of 3')).toBeTruthy();
});

test('filtering clamps deep scroll, retains ancestors and resolves pending hidden selection', () => {
    const entries = [
        segment(1),
        ...Array.from({ length: 4999 }, (_, i) => segment(i + 2, { parentId: 1 })),
    ];
    const tree = buildSegmentTree(entries);
    const onSelect = vi.fn();
    const view = render(<LogSegmentTree tree={tree} onSelect={onSelect} />);
    const rows = screen.getByRole('tree');
    rows.scrollTop = 4900 * 28;
    fireEvent.scroll(rows);
    fireEvent.change(screen.getByPlaceholderText('Filter steps...'), {
        target: { value: 'step-4999' },
    });
    expect(rows.scrollTop).toBe(0);
    expect(mountedIds()).toEqual([1, 4999]);
    expect(rowOf(1).classList.contains('Dimmed')).toBe(true);
    view.rerender(<LogSegmentTree tree={tree} onSelect={onSelect} selectedId={4900} />);
    expect(mountedIds()).toEqual([1, 4999]);
    expect(rows.scrollTop).toBe(0);
    fireEvent.change(screen.getByPlaceholderText('Filter steps...'), {
        target: { value: '' },
    });
    expectSelected(4900);
});

test('collapse clamps a scrolled loop without re-scrolling a settled selection on other toggles', () => {
    const tree = buildSegmentTree(loopSegments());
    const groupId = tree.roots[0].segment.id;
    render(<Harness tree={tree} />);
    fireEvent.click(rowOf(groupId).querySelector('.Caret')!);
    const rows = screen.getByRole('tree');
    rows.scrollTop = 94 * 28;
    fireEvent.scroll(rows);
    fireEvent.click(screen.getByLabelText('Collapse all'));
    expect(rows.scrollTop).toBe(0);
    expect(mountedIds()).toEqual(tree.roots.map((node) => node.segment.id));
    fireEvent.click(rowOf(groupId).querySelector('.Caret')!);
    fireEvent.click(rowOf(2).querySelector('.Caret')!);
    fireEvent.click(rowOf(3));
    expectSelected(3);
    rows.scrollTop = 94 * 28;
    fireEvent.scroll(rows);
    const offset = rows.scrollTop;
    const otherId = tree.roots[1].segment.id;
    fireEvent.click(rowOf(otherId).querySelector('.Caret')!);
    expect(rows.scrollTop).toBe(offset);
    expect(rowOf(otherId).getAttribute('aria-expanded')).toBe('true');
});

test('fresh snapshots update mounted status, counters and sampled running timing', () => {
    vi.spyOn(Date, 'now').mockReturnValue(6000);
    const entries = [
        segment(1, {
            createdAt: new Date(1000).toISOString(),
            status: SegmentStatus.RUNNING,
        }),
    ];
    const onSelect = vi.fn();
    const view = render(
        <LogSegmentTree
            tree={buildSegmentTree(entries)}
            processStatus={ProcessStatus.RUNNING}
            onSelect={onSelect}
        />
    );
    expect(rowOf(1).querySelector('.Duration')?.textContent).toBe('5.0s');
    expect(rowOf(1).querySelector('.StatusIcon')?.classList.contains('spinner')).toBe(true);
    vi.mocked(Date.now).mockReturnValue(9000);
    view.rerender(
        <LogSegmentTree
            tree={buildSegmentTree([...entries])}
            processStatus={ProcessStatus.RUNNING}
            onSelect={onSelect}
        />
    );
    expect(rowOf(1).querySelector('.Duration')?.textContent).toBe('8.0s');
    view.rerender(
        <LogSegmentTree
            tree={buildSegmentTree(entries)}
            processStatus={ProcessStatus.FINISHED}
            onSelect={onSelect}
        />
    );
    expect(rowOf(1).querySelector('.StatusIcon')?.classList.contains('spinner')).toBe(false);
    expect(rowOf(1).querySelector('.StatusIcon')?.classList.contains('question')).toBe(true);
    expect(rowOf(1).querySelector('.Duration')?.textContent).toBe('');
    view.rerender(
        <LogSegmentTree
            tree={buildSegmentTree([
                segment(1, {
                    createdAt: new Date(1000).toISOString(),
                    status: SegmentStatus.FAILED,
                    statusUpdatedAt: new Date(3000).toISOString(),
                    errors: 3,
                    warnings: 2,
                }),
            ])}
            processStatus={ProcessStatus.FINISHED}
            onSelect={onSelect}
        />
    );
    expect(rowOf(1).querySelector('.StatusIcon')?.getAttribute('title')).toBe('FAILED');
    expect(rowOf(1).querySelector('.Counter.Errors')?.textContent).toBe('3');
    expect(rowOf(1).querySelector('.Counter.Warnings')?.textContent).toBe('2');
    expect(rowOf(1).querySelector('.Duration')?.textContent).toBe('2.0s');
});

test('loops use ordinary selectable rows with gray expressions', () => {
    const tree = buildSegmentTree([
        segment(1, { kind: SegmentKind.LOOP, name: 'Deploy ${item}' }),
        segment(2, {
            parentId: 1,
            kind: SegmentKind.ITERATION,
            name: 'Deploy prod',
            iterationIndex: 0,
        }),
    ]);
    render(<Harness tree={tree} initialId={2} />);
    expect(rowOf(1).querySelector('.NameExpression')?.textContent).toBe('${item}');
    expect(rowOf(2).querySelector('.NameExpression')).toBeNull();
    expect(screen.queryByText('Container logs')).toBeNull();
    fireEvent.click(rowOf(1));
    expectSelected(1);
});

test('filtered branches can collapse and stay collapsed across refreshed results', () => {
    const entries = [
        segment(1, { status: SegmentStatus.RUNNING }),
        segment(2, { parentId: 1, status: SegmentStatus.RUNNING }),
        segment(3, { parentId: 2, status: SegmentStatus.RUNNING }),
    ];
    const onSelect = vi.fn();
    const view = render(<LogSegmentTree tree={buildSegmentTree(entries)} onSelect={onSelect} />);
    fireEvent.click(screen.getByText('Running'));
    expect(mountedIds()).toEqual([1, 2, 3]);
    fireEvent.click(rowOf(2).querySelector('.Caret')!);
    expect(mountedIds()).toEqual([1, 2]);
    expect(rowOf(2).getAttribute('aria-expanded')).toBe('false');
    view.rerender(<LogSegmentTree tree={buildSegmentTree([...entries])} onSelect={onSelect} />);
    expect(mountedIds()).toEqual([1, 2]);
    fireEvent.click(rowOf(2).querySelector('.Caret')!);
    expect(mountedIds()).toEqual([1, 2, 3]);
    fireEvent.click(screen.getByLabelText('Collapse all'));
    expect(mountedIds()).toEqual([1]);
    fireEvent.click(screen.getByText('All'));
    expect(mountedIds()).toEqual([1, 2, 3]);
    fireEvent.click(screen.getByText('Running'));
    expect(mountedIds()).toEqual([1, 2, 3]);
});

test('metadata refresh preserves a manually collapsed ancestor of the selected step', () => {
    const entries = [segment(1), segment(2, { parentId: 1 })];
    const view = render(<Harness tree={buildSegmentTree(entries)} initialId={2} />);
    expect(mountedIds()).toContain(2);
    fireEvent.click(rowOf(1).querySelector('.Caret')!);
    expect(mountedIds()).not.toContain(2);
    view.rerender(<Harness tree={buildSegmentTree([...entries, segment(3)])} initialId={2} />);
    expect(mountedIds()).not.toContain(2);
    expect(mountedIds()).toContain(3);
});

test('running duration and timeline advance without a new tree snapshot', () => {
    vi.useFakeTimers();
    vi.setSystemTime(6000);
    const tree = buildSegmentTree([
        segment(1, {
            createdAt: new Date(1000).toISOString(),
            status: SegmentStatus.OK,
            statusUpdatedAt: new Date(3000).toISOString(),
        }),
        segment(2, {
            createdAt: new Date(3000).toISOString(),
            status: SegmentStatus.RUNNING,
        }),
    ]);
    const view = render(
        <LogSegmentTree tree={tree} processStatus={ProcessStatus.RUNNING} onSelect={vi.fn()} />
    );
    const duration = (id: number) => rowOf(id).querySelector('.Duration')?.textContent;
    const bar = (id: number) => rowOf(id).querySelector('.Bar') as HTMLElement;
    expect(duration(1)).toBe('2.0s');
    expect(duration(2)).toBe('3.0s');
    expect(parseFloat(bar(2).style.left)).toBeCloseTo(40);
    expect(parseFloat(bar(2).style.width)).toBeCloseTo(60);

    act(() => {
        vi.advanceTimersByTime(4000);
    });
    expect(duration(1)).toBe('2.0s');
    expect(duration(2)).toBe('7.0s');
    expect(parseFloat(bar(2).style.left)).toBeCloseTo((2 / 9) * 100);
    expect(parseFloat(bar(2).style.width)).toBeCloseTo((7 / 9) * 100);
    expect(parseFloat(bar(1).style.width)).toBeCloseTo((2 / 9) * 100);
    expect(view.container.querySelector('.HeaderTimeline')?.textContent).toBe('0s9.0s');
    expect(vi.getTimerCount()).toBe(1);
    view.unmount();
    expect(vi.getTimerCount()).toBe(0);
});

test('starts the clock for running segments and stops when none remain', () => {
    vi.useFakeTimers();
    vi.setSystemTime(6000);
    const finished = segment(1, {
        createdAt: new Date(1000).toISOString(),
        status: SegmentStatus.OK,
        statusUpdatedAt: new Date(3000).toISOString(),
    });
    const onSelect = vi.fn();
    const view = render(
        <LogSegmentTree
            tree={buildSegmentTree([finished])}
            processStatus={ProcessStatus.RUNNING}
            onSelect={onSelect}
        />
    );
    expect(vi.getTimerCount()).toBe(0);
    act(() => {
        vi.advanceTimersByTime(4000);
    });
    view.rerender(
        <LogSegmentTree
            tree={buildSegmentTree([{ ...finished, status: SegmentStatus.RUNNING }])}
            processStatus={ProcessStatus.RUNNING}
            onSelect={onSelect}
        />
    );
    expect(rowOf(1).querySelector('.Duration')?.textContent).toBe('9.0s');
    expect(vi.getTimerCount()).toBe(1);
    view.rerender(
        <LogSegmentTree
            tree={buildSegmentTree([finished])}
            processStatus={ProcessStatus.RUNNING}
            onSelect={onSelect}
        />
    );
    expect(vi.getTimerCount()).toBe(0);
    act(() => {
        vi.advanceTimersByTime(4000);
    });
    expect(rowOf(1).querySelector('.Duration')?.textContent).toBe('2.0s');
});

test('stops the clock when the process finishes even if segment metadata still says running', () => {
    vi.useFakeTimers();
    vi.setSystemTime(6000);
    const tree = buildSegmentTree([
        segment(1, { createdAt: new Date(1000).toISOString(), status: SegmentStatus.RUNNING }),
    ]);
    const onSelect = vi.fn();
    const view = render(
        <LogSegmentTree tree={tree} processStatus={ProcessStatus.RUNNING} onSelect={onSelect} />
    );
    expect(vi.getTimerCount()).toBe(1);
    view.rerender(
        <LogSegmentTree tree={tree} processStatus={ProcessStatus.FINISHED} onSelect={onSelect} />
    );
    expect(vi.getTimerCount()).toBe(0);
    const content = view.container.innerHTML;
    act(() => {
        vi.advanceTimersByTime(4000);
    });
    expect(view.container.innerHTML).toBe(content);
});
