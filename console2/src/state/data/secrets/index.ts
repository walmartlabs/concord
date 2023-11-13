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

import { push as pushHistory } from 'connected-react-router';
import { Action, combineReducers, Reducer } from 'redux';
import { all, call, put, takeLatest } from 'redux-saga/effects';

import { ConcordId, ConcordKey, GenericOperationResult } from '../../../api/common';
import {
    deleteSecret as apiDelete,
    getSecretAccess,
    get as apiGet,
    list as apiList,
    renameSecret as apiRenameSecret,
    updateSecretAccess,
    updateSecretVisibility as apiUpdateSecretVisibility,
    SecretVisibility,
    SecretEntry,
    PaginatedSecretEntries
} from '../../../api/org/secret';
import {
    genericResult,
    handleErrors,
    makeErrorReducer,
    makeLoadingReducer,
    makeResponseReducer,
    nullReducer
} from '../common';
import {
    DeleteSecretRequest,
    DeleteSecretState,
    GetSecretRequest,
    SecretTeamAccessRequest,
    ListSecretsRequest,
    ListSecretsState,
    Pagination,
    PaginatedSecrets,
    RenameSecretRequest,
    RenameSecretState,
    SecretDataResponse,
    State,
    UpdateSecretTeamAccessState,
    UpdateSecretVisibilityResponse,
    UpdateSecretVisibilityRequest,
    UpdateSecretVisiblityState,
    SecretTeamAccessState
} from './types';
import { UpdateSecretTeamAccessRequest } from './types';
import { ResourceAccessEntry } from '../../../api/org';

// https://github.com/facebook/create-react-app/issues/6054
export * from './types';

const NAMESPACE = 'secrets';

const actionTypes = {
    GET_SECRET_REQUEST: `${NAMESPACE}/get/request`,
    LIST_SECRETS_REQUEST: `${NAMESPACE}/list/request`,
    SECRET_DATA_RESPONSE: `${NAMESPACE}/data/response`,

    DELETE_SECRET_REQUEST: `${NAMESPACE}/delete/request`,
    DELETE_SECRET_RESPONSE: `${NAMESPACE}/delete/response`,

    RENAME_SECRET_REQUEST: `${NAMESPACE}/rename/request`,
    RENAME_SECRET_RESPONSE: `${NAMESPACE}/rename/response`,

    UPDATE_SECRET_VISIBLITY_REQUEST: `${NAMESPACE}/visibility/request`,
    UPDATE_SECRET_VISIBLITY_RESPONSE: `${NAMESPACE}/visibility/response`,

    SECRET_TEAM_ACCESS_REQUEST: `${NAMESPACE}/secret/teamaccess/request`,
    SECRET_TEAM_ACCESS_RESPONSE: `${NAMESPACE}/secret/teamaccess/response`,

    UPDATE_SECRET_TEAM_ACCESS_REQUEST: `${NAMESPACE}/secret/team/access/update/request`,
    UPDATE_SECRET_TEAM_ACCESS_RESPONSE: `${NAMESPACE}/secret/team/access/update/response`,

    RESET_SECRET: `${NAMESPACE}/reset`
};

export const actions = {
    getSecret: (orgName: ConcordKey, secretName: ConcordKey): GetSecretRequest => ({
        type: actionTypes.GET_SECRET_REQUEST,
        orgName,
        secretName
    }),

    listSecrets: (
        orgName: ConcordKey,
        pagination: Pagination,
        filter?: string
    ): ListSecretsRequest => ({
        type: actionTypes.LIST_SECRETS_REQUEST,
        orgName,
        pagination,
        filter
    }),

    deleteSecret: (orgName: ConcordKey, secretName: ConcordKey): DeleteSecretRequest => ({
        type: actionTypes.DELETE_SECRET_REQUEST,
        orgName,
        secretName
    }),

    renameSecret: (
        orgName: ConcordKey,
        secretName: ConcordKey,
        newSecretName: ConcordKey
    ): RenameSecretRequest => ({
        type: actionTypes.RENAME_SECRET_REQUEST,
        orgName,
        secretName,
        newSecretName
    }),

    updateSecretVisibility: (
        orgName: ConcordKey,
        secretId: ConcordId,
        secretName: ConcordKey,
        visibility: SecretVisibility
    ): UpdateSecretVisibilityRequest => ({
        type: actionTypes.UPDATE_SECRET_VISIBLITY_REQUEST,
        orgName,
        secretId,
        secretName,
        visibility
    }),

    secretTeamAccess: (orgName: ConcordKey, secretName: ConcordKey): SecretTeamAccessRequest => ({
        type: actionTypes.SECRET_TEAM_ACCESS_REQUEST,
        orgName,
        secretName
    }),

    updateSecretTeamAccess: (
        orgName: ConcordKey,
        secretName: ConcordKey,
        teams: ResourceAccessEntry[]
    ): UpdateSecretTeamAccessRequest => ({
        type: actionTypes.UPDATE_SECRET_TEAM_ACCESS_REQUEST,
        orgName,
        secretName,
        teams
    }),

    reset: (): Action => ({
        type: actionTypes.RESET_SECRET
    })
};

const secretById: Reducer<PaginatedSecrets> = (
    state = {},
    action: SecretDataResponse | UpdateSecretVisibilityResponse
) => {
    switch (action.type) {
        case actionTypes.LIST_SECRETS_REQUEST: {
            return {};
        }
        case actionTypes.SECRET_DATA_RESPONSE: {
            const a = action as SecretDataResponse;

            if (a.error || !a.items) {
                return {};
            }

            const result = {};
            a.items.forEach((o) => {
                result[o.id] = o;
            });
            return { items: result, next: a.next };
        }
        case actionTypes.UPDATE_SECRET_VISIBLITY_RESPONSE: {
            const a = action as UpdateSecretVisibilityResponse;

            if (a.error) {
                return state;
            }

            if (!state.items) {
                return {};
            }

            state.items[a.secretId].visibility = a.visibility;
            return state;
        }
        default: {
            return state;
        }
    }
};

const listSecretsReducer = combineReducers<ListSecretsState>({
    running: makeLoadingReducer(
        [actionTypes.LIST_SECRETS_REQUEST],
        [actionTypes.SECRET_DATA_RESPONSE]
    ),
    error: makeErrorReducer([actionTypes.LIST_SECRETS_REQUEST], [actionTypes.SECRET_DATA_RESPONSE]),
    response: nullReducer()
});

const deleteSecretReducers = combineReducers<DeleteSecretState>({
    running: makeLoadingReducer(
        [actionTypes.DELETE_SECRET_REQUEST],
        [actionTypes.DELETE_SECRET_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.DELETE_SECRET_REQUEST],
        [actionTypes.DELETE_SECRET_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.DELETE_SECRET_RESPONSE, actionTypes.RESET_SECRET)
});

const renameSecretReducers = combineReducers<RenameSecretState>({
    running: makeLoadingReducer(
        [actionTypes.RENAME_SECRET_REQUEST],
        [actionTypes.RENAME_SECRET_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.RENAME_SECRET_REQUEST],
        [actionTypes.RENAME_SECRET_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.RENAME_SECRET_RESPONSE, actionTypes.RESET_SECRET)
});

const updateSecretVisibilityReducers = combineReducers<UpdateSecretVisiblityState>({
    running: makeLoadingReducer(
        [actionTypes.UPDATE_SECRET_VISIBLITY_REQUEST],
        [actionTypes.UPDATE_SECRET_VISIBLITY_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.UPDATE_SECRET_VISIBLITY_REQUEST],
        [actionTypes.UPDATE_SECRET_VISIBLITY_RESPONSE]
    ),
    response: makeResponseReducer(
        actionTypes.UPDATE_SECRET_VISIBLITY_RESPONSE,
        actionTypes.RESET_SECRET
    )
});

const secretTeamAccessReducers = combineReducers<SecretTeamAccessState>({
    running: makeLoadingReducer(
        [actionTypes.SECRET_TEAM_ACCESS_REQUEST],
        [actionTypes.SECRET_TEAM_ACCESS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.SECRET_TEAM_ACCESS_REQUEST],
        [actionTypes.SECRET_TEAM_ACCESS_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.SECRET_TEAM_ACCESS_RESPONSE)
});

const updateSecretTeamAccessReducers = combineReducers<UpdateSecretTeamAccessState>({
    running: makeLoadingReducer(
        [actionTypes.UPDATE_SECRET_TEAM_ACCESS_REQUEST],
        [actionTypes.UPDATE_SECRET_TEAM_ACCESS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.UPDATE_SECRET_TEAM_ACCESS_REQUEST],
        [actionTypes.UPDATE_SECRET_TEAM_ACCESS_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.UPDATE_SECRET_TEAM_ACCESS_RESPONSE)
});

export const reducers = combineReducers<State>({
    secretById, // TODO use makeEntityByIdReducer

    listSecrets: listSecretsReducer,
    deleteSecret: deleteSecretReducers,
    renameSecret: renameSecretReducers,
    updateSecretVisibility: updateSecretVisibilityReducers,
    secretTeamAccess: secretTeamAccessReducers,
    updateSecretTeamAccess: updateSecretTeamAccessReducers
});

export const selectors = {
    secretByName: (state: State, orgName: ConcordKey, secretName: ConcordKey) => {
        if (!state.secretById.items) {
            return;
        }

        for (const id of Object.keys(state.secretById.items)) {
            const p = state.secretById.items[id];
            if (p.orgName === orgName && p.name === secretName) {
                return p;
            }
        }

        return;
    },
    secretAccesTeams: (state: State, orgName: ConcordKey, projectName: ConcordKey) => {
        const p = state.secretTeamAccess.response ? state.secretTeamAccess.response.items : [];
        return p ? p : [];
    }
};

function* onGet({ orgName, secretName }: GetSecretRequest) {
    try {
        const response: SecretEntry = yield call(apiGet, orgName, secretName);
        yield put({
            type: actionTypes.SECRET_DATA_RESPONSE,
            items: [response] // normalizing the data
        });
    } catch (e) {
        yield handleErrors(actionTypes.SECRET_DATA_RESPONSE, e);
    }
}

function* onList({ orgName, pagination, filter }: ListSecretsRequest) {
    try {
        const response: PaginatedSecretEntries = yield call(
            apiList,
            orgName,
            pagination.offset,
            pagination.limit,
            filter
        );
        yield put({
            type: actionTypes.SECRET_DATA_RESPONSE,
            items: response.items,
            next: response.next
        });
    } catch (e) {
        yield handleErrors(actionTypes.SECRET_DATA_RESPONSE, e);
    }
}

function* onDelete({ orgName, secretName }: DeleteSecretRequest) {
    try {
        const response: GenericOperationResult = yield call(apiDelete, orgName, secretName);
        yield put(genericResult(actionTypes.DELETE_SECRET_RESPONSE, response));

        yield put(pushHistory(`/org/${orgName}/secret`));
    } catch (e) {
        yield handleErrors(actionTypes.DELETE_SECRET_RESPONSE, e);
    }
}

function* onRename({ orgName, secretName, newSecretName }: RenameSecretRequest) {
    try {
        const response: GenericOperationResult = yield call(
            apiRenameSecret,
            orgName,
            secretName,
            newSecretName
        );
        yield put(genericResult(actionTypes.RENAME_SECRET_RESPONSE, response));

        yield put(pushHistory(`/org/${orgName}/secret`));
    } catch (e) {
        yield handleErrors(actionTypes.RENAME_SECRET_RESPONSE, e);
    }
}

function* onUpdateVisibility({ orgName, secretName, secretId, visibility }: UpdateSecretVisibilityRequest) {
    try {
        yield call(apiUpdateSecretVisibility, orgName, secretName, visibility);
        yield put({
            type: actionTypes.UPDATE_SECRET_VISIBLITY_RESPONSE,
            secretId,
            visibility
        });

        yield put(pushHistory(`/org/${orgName}/secret/${secretName}`));
    } catch (e) {
        yield handleErrors(actionTypes.UPDATE_SECRET_VISIBLITY_RESPONSE, e);
    }
}

function* onGetSecretTeamAccess({ orgName, secretName }: SecretTeamAccessRequest) {
    try {
        const response: GenericOperationResult = yield call(getSecretAccess, orgName, secretName);
        yield put({
            type: actionTypes.SECRET_TEAM_ACCESS_RESPONSE,
            items: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.SECRET_TEAM_ACCESS_RESPONSE, e);
    }
}

function* onUpdateSecretTeamAccess({ orgName, secretName, teams }: UpdateSecretTeamAccessRequest) {
    try {
        const response: GenericOperationResult = yield call(
            updateSecretAccess,
            orgName,
            secretName,
            teams
        );
        yield put(genericResult(actionTypes.UPDATE_SECRET_TEAM_ACCESS_RESPONSE, response));
        yield put(actions.secretTeamAccess(orgName, secretName));
    } catch (e) {
        yield handleErrors(actionTypes.UPDATE_SECRET_TEAM_ACCESS_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([
        takeLatest(actionTypes.LIST_SECRETS_REQUEST, onList),
        takeLatest(actionTypes.GET_SECRET_REQUEST, onGet),
        takeLatest(actionTypes.DELETE_SECRET_REQUEST, onDelete),
        takeLatest(actionTypes.RENAME_SECRET_REQUEST, onRename),
        takeLatest(actionTypes.UPDATE_SECRET_VISIBLITY_REQUEST, onUpdateVisibility),
        takeLatest(actionTypes.SECRET_TEAM_ACCESS_REQUEST, onGetSecretTeamAccess),
        takeLatest(actionTypes.UPDATE_SECRET_TEAM_ACCESS_REQUEST, onUpdateSecretTeamAccess)
    ]);
};
