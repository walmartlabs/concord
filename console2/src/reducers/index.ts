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

import { RouterState, connectRouter } from 'connected-react-router';
import { combineReducers } from 'redux';
import { History } from 'history';

import { reducers as formsReducers, State as FormsState } from '../state/data/forms';
import { reducers as processesReducers, State as ProcessesState } from '../state/data/processes';
import { reducers as secretsReducer, State as SecretsState } from '../state/data/secrets';
import { reducers as teamReducers, State as TeamsState } from '../state/data/teams';

export interface State {
    forms: FormsState;
    processes: ProcessesState;
    router: RouterState;
    secrets: SecretsState;
    teams: TeamsState;
}

const reducers = (history: History) =>
    combineReducers({
        forms: formsReducers,
        processes: processesReducers,
        router: connectRouter(history),
        secrets: secretsReducer,
        teams: teamReducers
    });

export default reducers;
