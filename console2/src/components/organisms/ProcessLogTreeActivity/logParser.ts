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

export type LogLevel = 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';

export interface LogLine {
    // 1-based number of the line in the segment log
    n: number;
    // epoch ms, undefined for lines without a timestamp (e.g. stack traces)
    ts?: number;
    // continuation lines inherit the level of the line above
    level?: LogLevel;
    text: string;
}

// "2026-09-26T01:37:31.071+0000 [ERROR] message"
const LINE_RE =
    /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}(?:Z|[+-]\d{2}:?\d{2}))\s+\[(TRACE|DEBUG|INFO|WARN|ERROR)\s*\]\s?(.*)$/;

// "+0000" is not a valid ISO offset for Date.parse
const parseTimestamp = (s: string): number | undefined => {
    const t = Date.parse(s.replace(/([+-]\d{2})(\d{2})$/, '$1:$2'));
    return Number.isNaN(t) ? undefined : t;
};

export const parseLog = (text: string, firstLineNumber = 1): LogLine[] => {
    if (text.length === 0) {
        return [];
    }

    const raw = text.endsWith('\n') ? text.substring(0, text.length - 1) : text;
    const result: LogLine[] = [];
    let level: LogLevel | undefined;
    raw.split('\n').forEach((l, idx) => {
        const line = l.endsWith('\r') ? l.substring(0, l.length - 1) : l;
        const m = LINE_RE.exec(line);
        if (m) {
            level = m[2] as LogLevel;
            result.push({ n: firstLineNumber + idx, ts: parseTimestamp(m[1]), level, text: m[3] });
        } else {
            result.push({ n: firstLineNumber + idx, level, text: line });
        }
    });
    return result;
};

export type LogItem<T> = { line: LogLine } | { marker: T };

/**
 * Places the markers (e.g. nested steps) between the lines by time:
 * a marker goes before the first line logged after it started.
 */
export const mergeByTime = <T>(
    lines: LogLine[],
    markers: T[],
    markerTime: (m: T) => number
): LogItem<T>[] => {
    const sorted = [...markers].sort((a, b) => markerTime(a) - markerTime(b));
    const result: LogItem<T>[] = [];
    let mi = 0;
    let lastTs = Number.NEGATIVE_INFINITY;
    for (const line of lines) {
        const ts = line.ts ?? lastTs;
        while (mi < sorted.length && markerTime(sorted[mi]) < ts) {
            result.push({ marker: sorted[mi++] });
        }
        result.push({ line });
        lastTs = ts;
    }
    while (mi < sorted.length) {
        result.push({ marker: sorted[mi++] });
    }
    return result;
};

const URL_RE = /\bhttps?:\/\/[^\s<>"'`]+[^\s<>"'`.,;:!?)\]}]/g;
const INSTANCE_ID_TAG = /<concord:instanceId>([^<]*)<\/concord:instanceId>/g;

export type TextPart = { text: string } | { url: string } | { instanceId: string };

// splits a message into plain text, URLs and links to other processes
export const splitLinks = (text: string): TextPart[] => {
    const parts: TextPart[] = [];
    const re = new RegExp(`${INSTANCE_ID_TAG.source}|${URL_RE.source}`, 'g');
    let last = 0;
    for (let m = re.exec(text); m !== null; m = re.exec(text)) {
        if (m.index > last) {
            parts.push({ text: text.substring(last, m.index) });
        }
        parts.push(m[1] !== undefined ? { instanceId: m[1] } : { url: m[0] });
        last = m.index + m[0].length;
    }
    if (last < text.length) {
        parts.push({ text: text.substring(last) });
    }
    return parts;
};

export interface FlowSegment<S> {
    segment: S;
    // epoch ms
    start: number;
    lines: LogLine[];
    children: FlowSegment<S>[];
}

export type FlowItem<S> =
    | { kind: 'header'; segment: S; continued: boolean }
    | { kind: 'line'; segment: S; line: LogLine };

/**
 * Flattens the log of a segment and its nested segments into a single list: nested segments go
 * between the lines by time, each one starts with a header. When the parent's lines resume after
 * a nested segment, a "continued" header of the parent goes first, so every line has an owner.
 */
export const buildLogFlow = <S>(root: FlowSegment<S>): FlowItem<S>[] => {
    const out: FlowItem<S>[] = [];
    const emit = (fs: FlowSegment<S>, header: boolean) => {
        if (header) {
            out.push({ kind: 'header', segment: fs.segment, continued: false });
        }
        let afterChild = false;
        for (const item of mergeByTime(fs.lines, fs.children, (c) => c.start)) {
            if ('line' in item) {
                if (afterChild) {
                    out.push({ kind: 'header', segment: fs.segment, continued: true });
                    afterChild = false;
                }
                out.push({ kind: 'line', segment: fs.segment, line: item.line });
            } else {
                emit(item.marker, true);
                afterChild = true;
            }
        }
    };
    emit(root, false);
    return out;
};
