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

import { describe, expect, test } from 'vitest';

import { buildLogFlow, FlowSegment, mergeByTime, parseLog, splitLinks } from '../logParser';

describe('parseLog', () => {
    test('timestamps, levels and continuation lines', () => {
        const lines = parseLog(
            '2026-09-26T01:37:31.071+0000 [INFO ] started\n' +
                '2026-09-26T01:37:32.000+0000 [ERROR] boom\n' +
                '\tat com.example.Foo.bar(Foo.java:42)\n'
        );

        expect(lines).toHaveLength(3);
        expect(lines[0]).toEqual({
            n: 1,
            ts: Date.parse('2026-09-26T01:37:31.071Z'),
            level: 'INFO',
            text: 'started',
        });
        expect(lines[1].level).toBe('ERROR');
        // a stack trace line: no timestamp, the level of the line above
        expect(lines[2]).toEqual({
            n: 3,
            level: 'ERROR',
            text: '\tat com.example.Foo.bar(Foo.java:42)',
        });
    });

    test('plain lines', () => {
        expect(parseLog('')).toEqual([]);
        expect(parseLog('hello\r\nworld', 10)).toEqual([
            { n: 10, level: undefined, text: 'hello' },
            { n: 11, level: undefined, text: 'world' },
        ]);
    });
});

test('mergeByTime puts markers before the first line logged after them', () => {
    const lines = parseLog(
        '2026-09-26T00:00:01.000+0000 [INFO ] a\n' +
            'continued\n' +
            '2026-09-26T00:00:05.000+0000 [INFO ] b\n'
    );
    const t = (s: number) => Date.parse('2026-09-26T00:00:00.000Z') + s * 1000;
    const items = mergeByTime(lines, [t(3), t(0), t(9)], (m) => m);
    expect(items.map((i) => ('line' in i ? i.line.text : `@${(i.marker - t(0)) / 1000}`))).toEqual([
        '@0',
        'a',
        'continued',
        '@3',
        'b',
        '@9',
    ]);
});

test('splitLinks finds URLs and process links', () => {
    expect(
        splitLinks(
            'see https://example.com/a?b=1. child <concord:instanceId>abc-1</concord:instanceId> done'
        )
    ).toEqual([
        { text: 'see ' },
        { url: 'https://example.com/a?b=1' },
        { text: '. child ' },
        { instanceId: 'abc-1' },
        { text: ' done' },
    ]);
});

test('buildLogFlow flattens nested segments with headers', () => {
    const t = (s: number) => Date.parse('2026-09-26T00:00:00.000Z') + s * 1000;
    const log = (...lines: [number, string][]) =>
        parseLog(
            lines
                .map(
                    ([sec, msg]) =>
                        `2026-09-26T00:00:${String(sec).padStart(2, '0')}.000+0000 [INFO ] ${msg}`
                )
                .join('\n')
        );
    const seg = (
        name: string,
        start: number,
        lines: ReturnType<typeof log>,
        children: FlowSegment<string>[] = []
    ) => ({
        segment: name,
        start: t(start),
        lines,
        children,
    });

    const flow = buildLogFlow(
        seg('parent', 0, log([1, 'p1'], [9, 'p2']), [
            seg('child', 2, log([3, 'c1'], [6, 'c2']), [seg('grandchild', 4, log([5, 'g1']))]),
            // a nested step without output
            seg('empty', 7, []),
        ])
    );

    expect(
        flow.map((i) =>
            i.kind === 'header'
                ? `[${i.segment}${i.continued ? ' continued' : ''}]`
                : `${i.segment}:${i.line.text}`
        )
    ).toEqual([
        'parent:p1',
        '[child]',
        'child:c1',
        '[grandchild]',
        'grandchild:g1',
        '[child continued]',
        'child:c2',
        '[empty]',
        '[parent continued]',
        'parent:p2',
    ]);
});
