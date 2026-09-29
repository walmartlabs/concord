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
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';

import { ProcessStatus } from '../../../../api/process';
import { SegmentStatus } from '../../../../api/process/log';
import type { LogSegmentEntry } from '../../../../api/process/log';
import LogSegmentTree from '../LogSegmentTree';
import { buildSegmentTree } from '../segmentTree';

const segment = (id: number, extra: Partial<LogSegmentEntry> = {}): LogSegmentEntry => ({
    id,
    name: `step-${id}`,
    createdAt: new Date(1_000 + id).toISOString(),
    ...extra,
});

afterEach(cleanup);

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

test('error navigation excludes every row recovered by a later retry', () => {
    const tree = buildSegmentTree([
        segment(1, {
            correlationId: 'retry',
            attempt: 1,
            status: SegmentStatus.FAILED,
            errors: 1,
        }),
        segment(2, {
            parentId: 1,
            status: SegmentStatus.FAILED,
            errors: 2,
        }),
        segment(3, {
            correlationId: 'retry',
            attempt: 2,
            status: SegmentStatus.OK,
        }),
    ]);

    render(
        <LogSegmentTree
            tree={tree}
            processStatus={ProcessStatus.FINISHED}
            onSelect={vi.fn()}
        />
    );

    expect(screen.getByText('No errors')).toBeTruthy();
    expect(screen.getByLabelText('Previous error')).toHaveProperty('className');
    expect(screen.getByLabelText('Next error').classList.contains('disabled')).toBe(true);
    expect(tree.byId.get(2)?.recovered).toBe(true);
    expect(tree.byId.get(2)?.retried).toBeUndefined();
});
