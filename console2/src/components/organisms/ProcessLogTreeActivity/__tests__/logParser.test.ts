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

import { describe, expect, test, vi } from 'vitest';

import {
    buildLogFlow,
    FlowSegment,
    mergeByTime,
    parseLog,
    parseLogWindow,
    splitLinks,
} from '../logParser';
import type { ParsedLog } from '../logParser';

describe('incremental log parsing', () => {
    const encoder = new TextEncoder();
    const bytes = (text: string) => encoder.encode(text).length;
    const expected = (text: string, before: number) => {
        let offset = before;
        const raw = text.split('\n');
        return parseLog(text).map((line, i) => {
            const result = { ...line, offset };
            offset += bytes(raw[i] + (i < raw.length - 1 ? '\n' : ''));
            return result;
        });
    };

    test('matches a full parse across every possible append boundary', () => {
        const text =
            '2026-09-26T01:37:31.071+0000 [ERROR] ошибка 🔥\r\n' +
            '\tat example.run()\n\n' +
            '2026-09-26T01:37:32.000+0000 DOCKER: готово\r\n' +
            'continued\n' +
            '2026-09-26T01:37:33.000+0000 [WARN ] unfinished';
        const before = 4096;
        for (let split = 0; split <= text.length; split++) {
            const prefix = text.substring(0, split);
            const initial = parseLogWindow(prefix, before, before + bytes(prefix));
            const result = parseLogWindow(text, before, before + bytes(text), initial);
            expect(result.lines).toEqual(expected(text, before));
            expect(result.nextOffset).toBe(before + bytes(text));
        }
    });

    test('handles successive tiny appends and preserves complete line objects', () => {
        const text = '2026-09-26T01:37:31.071+0000 [ERROR] boom\r\n\tat run()\n';
        let parsed: ParsedLog | undefined;
        for (let end = 1; end <= text.length; end++) {
            const prefix = text.substring(0, end);
            const previous = parsed;
            parsed = parseLogWindow(prefix, 0, bytes(prefix), previous);
            expect(parsed.lines).toEqual(expected(prefix, 0));
            if (previous && previous.lines.length > 1) {
                expect(parsed.lines[0]).toBe(previous.lines[0]);
            }
        }
    });

    test('parses only the new timestamp after a large completed prefix', () => {
        const line = '2026-09-26T01:37:31.071+0000 [INFO ] old\n';
        const prefix = line.repeat(10_000);
        const initial = parseLogWindow(prefix, 0, bytes(prefix));
        const timestampParser = vi.spyOn(Date, 'parse');
        try {
            const text = prefix + line;
            const next = parseLogWindow(text, 0, bytes(text), initial);
            expect(timestampParser).toHaveBeenCalledTimes(1);
            expect(next.lines).toHaveLength(10_001);
            expect(next.lines[0]).toBe(initial.lines[0]);
            expect(next.lines[9_999]).toBe(initial.lines[9_999]);
            expect(parseLogWindow(text, 0, bytes(text), next)).toBe(next);
            expect(timestampParser).toHaveBeenCalledTimes(1);
        } finally {
            timestampParser.mockRestore();
        }
    });

    test('rebuilds offsets, numbers and inherited levels when history is prepended', () => {
        const prefix = '2026-09-26T01:37:31.071+0000 [ERROR] ошибка\n';
        const tail = '\tat run()\nunfinished';
        const initial = parseLogWindow(tail, bytes(prefix), bytes(prefix + tail));
        const text = prefix + tail;
        const history = parseLogWindow(text, 0, bytes(text), initial);
        expect(history.lines).toEqual(expected(text, 0));
        expect(history.lines[1].level).toBe('ERROR');
        const appended = text + ' line\n';
        expect(parseLogWindow(appended, 0, bytes(appended), history).lines).toEqual(
            expected(appended, 0)
        );
    });

    test('rebuilds when the cached log is replaced by a shorter window', () => {
        const original = 'first\nsecond\n';
        const initial = parseLogWindow(original, 0, bytes(original));
        const replacement = 'new\n';
        const result = parseLogWindow(replacement, 0, bytes(replacement), initial);
        expect(result.lines).toEqual(expected(replacement, 0));
    });
});

describe('parseLog', () => {
    test.each(['DOCKER', 'ANSIBLE'])('parses %s output without a log level', (prefix) => {
        const message = `${prefix}: [ERROR] is part of the process output`;
        const lines = parseLog(
            '2026-09-26T01:37:30.000+0000 [ERROR] previous error\n' +
                `2026-09-26T01:37:31.071+0000 ${message}\r\n` +
                '    continued process output\n' +
                '2026-09-26T01:37:32.000+0000 [WARN ] next warning\n'
        );

        expect(lines).toHaveLength(4);
        expect(lines[1]).toEqual({
            n: 2,
            ts: Date.parse('2026-09-26T01:37:31.071Z'),
            level: undefined,
            text: message,
        });
        expect(lines[2]).toEqual({
            n: 3,
            level: undefined,
            text: '    continued process output',
        });
        expect(lines[3]).toEqual({
            n: 4,
            ts: Date.parse('2026-09-26T01:37:32.000Z'),
            level: 'WARN',
            text: 'next warning',
        });
    });

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

test('timestamped processLog lines reset the level inherited by continuation lines', () => {
    const lines = parseLog(
        '2026-09-26T00:00:01.000+0000 [ERROR] failed\n' +
            '2026-09-26T00:00:02.000+0000 process output\n' +
            'continued\n'
    );
    expect(lines[1]).toEqual({
        n: 2,
        ts: Date.parse('2026-09-26T00:00:02.000Z'),
        level: undefined,
        text: 'process output',
    });
    expect(lines[2]).toEqual({ n: 3, level: undefined, text: 'continued' });
});

test('buildLogFlow interleaves processLog output with nested steps', () => {
    const flow = buildLogFlow({
        segment: 'parent',
        start: Date.parse('2026-09-26T00:00:00.000Z'),
        lines: parseLog(
            '2026-09-26T00:00:01.000+0000 before\n' + '2026-09-26T00:00:03.000+0000 after\n'
        ),
        children: [
            {
                segment: 'child',
                start: Date.parse('2026-09-26T00:00:02.000Z'),
                lines: parseLog('2026-09-26T00:00:02.000+0000 child\n'),
                children: [],
            },
        ],
    });
    expect(
        flow
            .filter((item) => item.kind === 'line')
            .map((item) => item.kind === 'line' && item.line.text)
    ).toEqual(['before', 'child', 'after']);
});
