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
import { describe, expect, it } from 'vitest';
import {
    createFormHost,
    isInstanceId,
    isStartMessage,
    MAX_ARGUMENT_SIZE,
    PROTOCOL,
} from './bridge';

const start = (args: unknown = { name: 'test' }) => ({
    protocol: PROTOCOL,
    type: 'start',
    channel: 'nonce',
    arguments: args,
});

describe('restricted process card protocol', () => {
    it('uses a trusted relay with a card-specific frame navigation policy', () => {
        const host = createFormHost('card/id"><script>', 'nonce', 'https://concord.example');
        expect(host).toContain(
            'frame-src https://concord.example/api/v1/processcard/card%2Fid%22%3E%3Cscript%3E/form;'
        );
        expect(host).toContain('sandbox="allow-scripts"');
        expect(host).not.toContain('allow-same-origin');
        expect(host).toContain('event.source === frame.contentWindow');
        expect(host).toContain("script-src 'nonce-nonce'");
    });
    it('accepts JSON arguments, not request configuration', () => {
        expect(isStartMessage(start({ nested: [true, null, 2, 'x'] }), 'nonce')).toBe(true);
        for (const extra of ['url', 'headers', 'method', 'org', 'project', 'repo', 'entryPoint']) {
            expect(isStartMessage({ ...start(), [extra]: 'override' }, 'nonce')).toBe(false);
        }
    });

    it('rejects unknown messages, channels and non-JSON or oversized arguments', () => {
        for (const value of [
            null,
            [],
            {},
            { ...start(), type: 'navigate' },
            { ...start(), channel: 'other' },
            start(null),
            start([]),
            start({ value: undefined }),
            start({ value: NaN }),
            start({ value: new Date() }),
            start({ value: 'x'.repeat(MAX_ARGUMENT_SIZE) }),
            start(JSON.parse('{"__proto__":{}}')),
        ]) {
            expect(isStartMessage(value, 'nonce')).toBe(false);
        }
    });

    it('rejects cyclic and excessively deep structured clones', () => {
        const cycle: Record<string, unknown> = {};
        cycle.cycle = cycle;
        expect(isStartMessage(start(cycle), 'nonce')).toBe(false);
        let deep = {};
        for (let i = 0; i < 18; i++) deep = { nested: deep };
        expect(isStartMessage(start(deep), 'nonce')).toBe(false);
    });

    it('only accepts generated UUID process navigation targets', () => {
        expect(isInstanceId('12345678-1234-1234-1234-123456789abc')).toBe(true);
        for (const value of ['javascript:alert(1)', '//evil.example', '../admin', null]) {
            expect(isInstanceId(value)).toBe(false);
        }
    });
});
