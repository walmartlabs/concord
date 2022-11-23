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
import { queryParams } from '../common';
import { AnsibleStatus, SearchFilter, SortField, SortOrder } from '../process/ansible';

test('queryParams accepts an object of key value pairs.  e.g. { key: value, ... }', () => {
    const actual = queryParams({ param1: '123', param2: 'abc' });
    const expected = 'param1=123&param2=abc';
    expect(actual).toEqual(expected);
});

test('queryParams handles parameters with period characters in key name', () => {
    const actual = queryParams({ 'param.1': '123', 'param.2': 'abc' });
    const expected = 'param.1=123&param.2=abc';
    expect(actual).toEqual(expected);
});

test('queryParams accepts numbers as values', () => {
    const actual = queryParams({ param: 1, param2: 999 });
    const expected = 'param=1&param2=999';
    expect(actual).toEqual(expected);
});

test('queryParams handles multiple boolean values', () => {
    const actual = queryParams({ param: true, param2: false });
    const expected = 'param=true&param2=false';
    expect(actual).toEqual(expected);
});

test('queryParams handles undefined values', () => {
    const actual = queryParams({ param: undefined, param2: 'works' });
    const expected = 'param2=works';
    expect(actual).toEqual(expected);
});

test('queryParams handles SearchFilter type', () => {
    const filters: SearchFilter = {
        host: 'host',
        hostGroup: 'host-group',
        limit: 1,
        offset: 10,
        status: AnsibleStatus.CHANGED,
        sortField: SortField.DURATION,
        sortBy: SortOrder.DESC
    };

    const actual = queryParams({ ...filters });
    const expected = 'host=host&hostGroup=host-group&limit=1&offset=10&status=CHANGED&sortField=DURATION&sortBy=DESC';
    expect(actual).toEqual(expected);
});
