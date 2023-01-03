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

import { RouterState, connectRouter } from 'connected-react-router';
import { combineReducers } from 'redux';
import { History } from 'history';

import { reducers as formsReducers, State as FormsState } from '../state/data/forms';
import { reducers as processesReducers, State as ProcessesState } from '../state/data/processes';
import { reducers as projectsReducer, State as ProjectsState } from '../state/data/projects';
import { reducers as searchReducers, State as SearchState } from '../state/data/search';
import { reducers as secretsReducer, State as SecretsState } from '../state/data/secrets';
import { reducers as teamReducers, State as TeamsState } from '../state/data/teams';
import { reducers as triggersReducer, State as TriggersState } from '../state/data/triggers';

export interface State {
    forms: FormsState;
    processes: ProcessesState;
    projects: ProjectsState;
    router: RouterState;
    search: SearchState;
    secrets: SecretsState;
    teams: TeamsState;
    triggers: TriggersState;
}

const reducers = (history: History) =>
    combineReducers({
        forms: formsReducers,
        processes: processesReducers,
        projects: projectsReducer,
        router: connectRouter(history),
        search: searchReducers,
        secrets: secretsReducer,
        teams: teamReducers,
        triggers: triggersReducer
    });

export default reducers;
