/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import { parseQueryParams } from '../common';

test('parseQueryParams handles one query parameter', () => {
    const actual = parseQueryParams('http://localhost:3000/#/org/?param1=123');
    const expected = { param1: '123' };
    expect(actual).toEqual(expected);
});

test('parseQueryParams handles more than one query parameter', () => {
    const actual = parseQueryParams('http://localhost:3000/#/org/?param1=123&param2=abc');
    const expected = { param1: '123', param2: 'abc' };
    expect(actual).toEqual(expected);
});

test('parseQueryParams handles periods in a query parameter', () => {
    const result = parseQueryParams('http://localhost:3000/#/org/?param.1=123&param.2=abc');
    const expected = { 'param.1': '123', 'param.2': 'abc' };
    expect(result).toEqual(expected);
});

test('parseQueryParams handles url with no question mark "?"', () => {
    const actual = parseQueryParams('http://localhost:3000/#/org/param1=123');
    const expected = {};
    expect(actual).toEqual(expected);
});

test('parseQueryParams handles url with no params', () => {
    const result = parseQueryParams('http://localhost:3000/#/org/param1=123');
    const expected = {};
    expect(result).toEqual(expected);
});
