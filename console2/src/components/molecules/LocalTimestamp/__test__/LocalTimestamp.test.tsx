/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import { render } from 'react-testing-library';
import React from 'react';
import LocalTimeStamp from '..';

test('Accepts date input of 2018-02-05 19:03:19', () => {
    const { container } = render(<LocalTimeStamp value={'2018-02-05 19:03:19'} />);
    expect(container.innerHTML).toContain('2018-02-05 19:03:19');
});

test('Renders a message if date format is invalid', () => {
    const { container } = render(<LocalTimeStamp value={' '} />);
    expect(container.innerHTML).toContain('Invalid Date');
});

test('Renders a message if bad date value is provided', () => {
    const { container } = render(<LocalTimeStamp value={''} />);
    expect(container.innerHTML).toContain('Bad date value');
});
