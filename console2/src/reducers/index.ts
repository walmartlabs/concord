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

import { routerReducer, RouterState } from 'react-router-redux';
import { combineReducers, Reducer } from 'redux';

import { reducers as formsReducers, State as FormsState } from '../state/data/forms';
import { reducers as loginReducers, State as LoginState } from '../components/organisms/Login';
import { reducers as orgsReducer, State as OrgsState } from '../state/data/orgs';
import { reducers as processesReducers, State as ProcessesState } from '../state/data/processes';
import { reducers as projectsReducer, State as ProjectsState } from '../state/data/projects';
import { reducers as searchReducers, State as SearchState } from '../state/data/search';
import { reducers as secretsReducer, State as SecretsState } from '../state/data/secrets';
import { reducers as sessionReducers, State as SessionState } from '../state/session';
import { reducers as tokensReducers, State as TokensState } from '../state/data/apiTokens';
import { reducers as teamReducers, State as TeamsState } from '../state/data/teams';
import { reducers as triggersReducer, State as TriggersState } from '../state/data/triggers';
import {
    reducers as userActivityReducer,
    State as UserActivityState
} from '../state/data/userActivity';

export interface State {
    forms: FormsState;
    login: LoginState;
    orgs: OrgsState;
    processes: ProcessesState;
    projects: ProjectsState;
    router: RouterState;
    search: SearchState;
    secrets: SecretsState;
    session: SessionState;
    teams: TeamsState;
    tokens: TokensState;
    triggers: TriggersState;
    userActivity: UserActivityState;
}

const reducers: Reducer<State> = combineReducers({
    forms: formsReducers,
    login: loginReducers,
    orgs: orgsReducer,
    processes: processesReducers,
    projects: projectsReducer,
    router: routerReducer,
    search: searchReducers,
    secrets: secretsReducer,
    session: sessionReducers,
    teams: teamReducers,
    tokens: tokensReducers,
    triggers: triggersReducer,
    userActivity: userActivityReducer
});

export default reducers;
