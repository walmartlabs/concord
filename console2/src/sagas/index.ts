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

import { all, fork } from 'redux-saga/effects';

import { sagas as Forms } from '../state/data/forms';
import { sagas as Processes } from '../state/data/processes';
import { sagas as Projects } from '../state/data/projects';
import { sagas as Search } from '../state/data/search';
import { sagas as Secrets } from '../state/data/secrets';
import { sagas as Teams } from '../state/data/teams';
import { sagas as Triggers } from '../state/data/triggers';

export default function* root() {
    yield all([
        fork(Forms),
        fork(Processes),
        fork(Projects),
        fork(Search),
        fork(Secrets),
        fork(Teams),
        fork(Triggers)
    ]);
}
