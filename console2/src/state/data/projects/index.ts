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

import { combineReducers } from 'redux';
import { all, call, put, takeLatest } from 'redux-saga/effects';

import { ConcordKey, GenericOperationResult } from '../../../api/common';
import { ResourceAccessEntry } from '../../../api/org';
import {
    getProjectAccess,
    updateProjectAccess,
} from '../../../api/org/project';
import {
    genericResult,
    handleErrors,
    makeErrorReducer,
    makeLoadingReducer,
    makeResponseReducer
} from '../common';
import {
    ProjectTeamAccessRequest,
    ProjectTeamAccessState,
    State,
    UpdateProjectTeamAccessRequest,
    UpdateProjectTeamAccessState
} from './types';

// https://github.com/facebook/create-react-app/issues/6054
export * from './types';

const NAMESPACE = 'projects';

const actionTypes = {
    PROJECT_TEAM_ACCESS_REQUEST: `${NAMESPACE}/project/teamaccess/request`,
    PROJECT_TEAM_ACCESS_RESPONSE: `${NAMESPACE}/project/teamaccess/response`,

    UPDATE_PROJECT_TEAM_ACCESS_REQUEST: `${NAMESPACE}/project/teamaccess/update/request`,
    UPDATE_PROJECT_TEAM_ACCESS_RESPONSE: `${NAMESPACE}/project/teamaccess/update/response`,
    RESET_PROJECT_TEAM_ACCESS: `${NAMESPACE}/project/teamaccess/reset`
};

export const actions = {
    getTeamAccess: (orgName: ConcordKey, projectName: ConcordKey): ProjectTeamAccessRequest => ({
        type: actionTypes.PROJECT_TEAM_ACCESS_REQUEST,
        orgName,
        projectName
    }),

    updateTeamAccess: (
        orgName: ConcordKey,
        projectName: ConcordKey,
        teams: ResourceAccessEntry[]
    ): UpdateProjectTeamAccessRequest => ({
        type: actionTypes.UPDATE_PROJECT_TEAM_ACCESS_REQUEST,
        orgName,
        projectName,
        teams
    }),
};

const getTeamAccessReducers = combineReducers<ProjectTeamAccessState>({
    running: makeLoadingReducer(
        [actionTypes.PROJECT_TEAM_ACCESS_REQUEST],
        [actionTypes.PROJECT_TEAM_ACCESS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.PROJECT_TEAM_ACCESS_REQUEST],
        [actionTypes.PROJECT_TEAM_ACCESS_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.PROJECT_TEAM_ACCESS_RESPONSE)
});

const updateTeamAccessReducers = combineReducers<UpdateProjectTeamAccessState>({
    running: makeLoadingReducer(
        [actionTypes.UPDATE_PROJECT_TEAM_ACCESS_REQUEST],
        [actionTypes.UPDATE_PROJECT_TEAM_ACCESS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.UPDATE_PROJECT_TEAM_ACCESS_REQUEST],
        [actionTypes.UPDATE_PROJECT_TEAM_ACCESS_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.UPDATE_PROJECT_TEAM_ACCESS_RESPONSE)
});

export const reducers = combineReducers<State>({
    projectTeamAccess: getTeamAccessReducers,
    updateProjectTeamAccess: updateTeamAccessReducers
});

export const selectors = {

    projectAccesTeams: (state: State, orgName: ConcordKey, projectName: ConcordKey) => {
        const p = state.projectTeamAccess.response ? state.projectTeamAccess.response.items : [];
        return p ? p : [];
    }
};

function* onGetTeamAccess({ orgName, projectName }: ProjectTeamAccessRequest) {
    try {
        const response: GenericOperationResult = yield call(getProjectAccess, orgName, projectName);
        yield put({
            type: actionTypes.PROJECT_TEAM_ACCESS_RESPONSE,
            items: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.PROJECT_TEAM_ACCESS_RESPONSE, e);
    }
}

function* onUpdateTeamAccess({ orgName, projectName, teams }: UpdateProjectTeamAccessRequest) {
    try {
        const response: GenericOperationResult = yield call(
            updateProjectAccess,
            orgName,
            projectName,
            teams
        );
        yield put(genericResult(actionTypes.UPDATE_PROJECT_TEAM_ACCESS_RESPONSE, response));
        yield put(actions.getTeamAccess(orgName, projectName));
    } catch (e) {
        yield handleErrors(actionTypes.UPDATE_PROJECT_TEAM_ACCESS_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([
        takeLatest(actionTypes.PROJECT_TEAM_ACCESS_REQUEST, onGetTeamAccess),
        takeLatest(actionTypes.UPDATE_PROJECT_TEAM_ACCESS_REQUEST, onUpdateTeamAccess)
    ]);
};
