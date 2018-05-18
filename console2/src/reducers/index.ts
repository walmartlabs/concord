/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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
import { routerReducer, RouterState } from 'react-router-redux';
import { combineReducers, Reducer } from 'redux';

import { reducers as loginReducers, State as LoginState } from '../components/organisms/Login';
import { reducers as orgsReducer, State as OrgsState } from '../state/data/orgs';
import { reducers as processesReducers, State as ProcessesState } from '../state/data/processes';
import { reducers as projectsReducer, State as ProjectsState } from '../state/data/projects';
import { reducers as secretsReducer, State as SecretsState } from '../state/data/secrets';
import { reducers as formsReducers, State as FormsState } from '../state/data/forms';
import { reducers as teamReducers, State as TeamsState } from '../state/data/teams';
import { reducers as searchReducers, State as SearchState } from '../state/data/search';
import { reducers as sessionReducers, State as SessionState } from '../state/session';

export interface State {
    session: SessionState;
    login: LoginState;
    orgs: OrgsState;
    projects: ProjectsState;
    secrets: SecretsState;
    processes: ProcessesState;
    teams: TeamsState;
    forms: FormsState;
    search: SearchState;
    router: RouterState;
}

const reducers: Reducer<State> = combineReducers({
    session: sessionReducers,
    login: loginReducers,
    router: routerReducer,
    orgs: orgsReducer,
    projects: projectsReducer,
    secrets: secretsReducer,
    processes: processesReducers,
    teams: teamReducers,
    search: searchReducers,
    forms: formsReducers
});

export default reducers;
