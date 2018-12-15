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

import * as React from 'react';
import * as ReactDOM from 'react-dom';
import App from './';

describe('timestamp', () => {
    it('renders with good data', () => {
        const div = document.createElement('div');
        ReactDOM.render(<App value={'2018-02-05 19:03:19'} />, div);
    });

    it('renders with bad data', () => {
        const div = document.createElement('div');
        ReactDOM.render(<App value={' '} />, div);
    });
});
