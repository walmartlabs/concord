/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import { parseNestedQueryParams } from '../common';

test('parseNestedQueryParams works with a single value', () => {
    const actual = parseNestedQueryParams({ 'x.y.z': '123' }, ['x']);
    const expected = { x: { y: { z: '123' } } };
    expect(actual).toEqual(expected);
});

test('parseNestedQueryParams works with multiple nested values', () => {
    const actual = parseNestedQueryParams({ normalOne: 'true', 'x.y.a': '123', 'x.y.b': '234' }, [
        'x'
    ]);
    const expected = { normalOne: 'true', x: { y: { b: '234', a: '123' } } };
    expect(actual).toEqual(expected);
});
