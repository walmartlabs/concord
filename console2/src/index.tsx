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

import * as React from 'react';
import * as ReactDOM from 'react-dom';

import 'typeface-lato';
import 'semantic-ui-css/semantic.min.css';

import App from './App';
import './index.css';

const rootEl = document.getElementById('root') as HTMLElement;

ReactDOM.render(<App />, rootEl);

// @ts-ignore
if (module.hot) {
    // @ts-ignore
    module.hot.accept('./App', () => {
        const NextApp = require('./App').default;
        ReactDOM.render(<NextApp />, rootEl);
    });
}

// remove any old service worker
if (navigator.serviceWorker) {
    navigator.serviceWorker
        .getRegistrations()
        .then((regs) => regs.forEach((reg) => reg.unregister()));
}
