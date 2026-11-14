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
import { cleanup, render } from '@testing-library/react';
import { afterEach, expect, test } from 'vitest';
import SegmentName, { nameParts } from '../SegmentName';

afterEach(cleanup);

test('dims multiple complete expressions without changing the name', () => {
    const name = 'Deploy ${item.toUpperCase()} to ${env}';
    const { container } = render(<SegmentName name={name} />);
    expect(container.textContent).toBe(name);
    expect(Array.from(container.querySelectorAll('.NameExpression'), (e) => e.textContent)).toEqual(
        ['${item.toUpperCase()}', '${env}']
    );
});

test('handles nested collections and braces inside quoted strings', () => {
    const name = "Deploy ${item.replace('}', '{')} / ${{'key': {'nested': item}}}";
    expect(
        nameParts(name)
            .filter((p) => p.expression)
            .map((p) => p.text)
    ).toEqual(["${item.replace('}', '{')}", "${{'key': {'nested': item}}}"]);
    expect(
        nameParts(name)
            .map((p) => p.text)
            .join('')
    ).toBe(name);
});

test('escaped quotes do not end strings within expressions', () => {
    const name = 'Deploy $' + String.raw`{item.replace('\'}', '')}`;
    expect(nameParts(name)).toEqual([
        { text: 'Deploy ', expression: false },
        { text: name.slice(7), expression: true },
    ]);
});

test('leaves incomplete expressions as literal text', () => {
    expect(nameParts('Deploy ${item')).toEqual([{ text: 'Deploy ${item', expression: false }]);
});

test('names are safe text even if they contain expression syntax or HTML', () => {
    const name = '<b>${literal}</b>';
    const { container } = render(<SegmentName name={name} />);
    expect(container.textContent).toBe(name);
    expect(container.querySelector('.NameExpression')?.textContent).toBe('${literal}');
    expect(container.querySelector('b')).toBeNull();
});
